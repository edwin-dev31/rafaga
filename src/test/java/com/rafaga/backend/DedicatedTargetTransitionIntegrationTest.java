package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.port.DeploymentTargetHealthProbe;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@Execution(ExecutionMode.SAME_THREAD)
class DedicatedTargetTransitionIntegrationTest extends TenantIsolationTestBase {
    private static final String DIRECTORY_TOKEN = "rafaga_test_directory_internal_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private DeploymentTargetHealthProbe healthProbe;

    @Test
    void routesSharedDedicatedDbAndDedicatedInstanceTenantsAndSupportsReversibleTransition() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String dedicatedDbTenantId = "ded-db-" + suffix;
        String dedicatedDbHost = "ded-db-" + suffix + ".routing.test";
        String vipTenantId = "vip-" + suffix;
        String vipHost = "vip-" + suffix + ".routing.test";
        String targetId = "vip-target-" + suffix;
        String targetOrigin = "http://vip-" + suffix + ".internal:3117";

        try {
            tenants.register(new TenantConfig(
                    dedicatedDbTenantId, "Dedicated DB Store", List.of(dedicatedDbHost),
                    DeploymentMode.DEDICATED_DB, null, "pro", TenantStatus.ACTIVE, Map.of("catalog", true)));
            tenants.register(new TenantConfig(
                    vipTenantId, "VIP Store", List.of(vipHost),
                    DeploymentMode.DEDICATED_INSTANCE, null, "enterprise", TenantStatus.ACTIVE, Map.of("catalog", true)));

            when(healthProbe.isHealthy("http", targetOrigin, "/actuator/health/readiness")).thenReturn(true);

            // 1. Register dedicated backend target (starts in PREPARING, not routable)
            mockMvc.perform(post("/api/admin/deployment-targets")
                            .with(superAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "id", targetId,
                                    "targetType", "DEDICATED_INSTANCE",
                                    "protocol", "http",
                                    "privateOrigin", targetOrigin,
                                    "healthPath", "/actuator/health/readiness"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PREPARING"));

            // 2. Verify live health -> HEALTHY
            mockMvc.perform(post("/api/admin/deployment-targets/{targetId}/verify-health", targetId)
                            .with(superAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HEALTHY"));

            // 3. Controlled transition: prepare -> validate -> activate
            MvcResult prepared = mockMvc.perform(post("/api/admin/tenants/{tenantId}/target-transitions/prepare", vipTenantId)
                            .with(superAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "domain", vipHost,
                                    "toTargetId", targetId))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.state").value("PREPARED"))
                    .andReturn();
            String transitionId = objectMapper.readTree(prepared.getResponse().getContentAsString()).get("id").asString();

            mockMvc.perform(post("/api/admin/tenants/{tenantId}/target-transitions/{id}/validate", vipTenantId, transitionId)
                            .with(superAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("dataValidationChecksum", "sha256:verified-ok"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("VALIDATED"));

            mockMvc.perform(post("/api/admin/tenants/{tenantId}/target-transitions/{id}/activate", vipTenantId, transitionId)
                            .with(superAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ACTIVATED"));

            // 4. Verify directory exposes SHARED_POOL for shared & dedicated-db, and DEDICATED_INSTANCE for VIP
            TenantDirectorySnapshot snapshot = fetchDirectory(-1);
            assertThat(snapshot.activeEntries()).anySatisfy(entry -> {
                assertThat(entry.hostname()).isEqualTo(HOST_A);
                assertThat(entry.targetType()).isEqualTo(DeploymentTargetType.SHARED_POOL);
                assertThat(entry.targetId()).isEqualTo("shared-pool");
            });
            assertThat(snapshot.activeEntries()).anySatisfy(entry -> {
                assertThat(entry.hostname()).isEqualTo(dedicatedDbHost);
                assertThat(entry.targetType()).isEqualTo(DeploymentTargetType.SHARED_POOL);
                assertThat(entry.targetId()).isEqualTo("shared-pool");
            });
            assertThat(snapshot.activeEntries()).anySatisfy(entry -> {
                assertThat(entry.hostname()).isEqualTo(vipHost);
                assertThat(entry.targetType()).isEqualTo(DeploymentTargetType.DEDICATED_INSTANCE);
                assertThat(entry.targetId()).isEqualTo(targetId);
            });

            // 5. Rollback restores VIP domain to shared-pool and records audit trail
            mockMvc.perform(post("/api/admin/tenants/{tenantId}/target-transitions/{id}/rollback", vipTenantId, transitionId)
                            .with(superAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "Canary check rollback"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ROLLED_BACK"));

            TenantDirectorySnapshot afterRollback = fetchDirectory(-1);
            assertThat(afterRollback.activeEntries()).anySatisfy(entry -> {
                assertThat(entry.hostname()).isEqualTo(vipHost);
                assertThat(entry.targetType()).isEqualTo(DeploymentTargetType.SHARED_POOL);
                assertThat(entry.targetId()).isEqualTo("shared-pool");
            });

            mockMvc.perform(get("/api/admin/tenants/{tenantId}/target-transitions", vipTenantId)
                            .with(superAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(transitionId))
                    .andExpect(jsonPath("$[0].state").value("ROLLED_BACK"));
        } finally {
            jdbcTemplate.update("DELETE FROM tenants WHERE id IN (?, ?)", dedicatedDbTenantId, vipTenantId);
            jdbcTemplate.update("DELETE FROM deployment_targets WHERE id = ?", targetId);
        }
    }

    private TenantDirectorySnapshot fetchDirectory(long afterVersion) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/internal/tenant-directory")
                        .param("afterVersion", Long.toString(afterVersion))
                        .header("X-Tenant-Directory-Token", DIRECTORY_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TenantDirectorySnapshot.class);
    }

    private RequestPostProcessor superAdmin() {
        return jwt()
                .jwt(token -> token.claim("tenant_id", TENANT_A).claim("roles", List.of("SUPER_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }
}
