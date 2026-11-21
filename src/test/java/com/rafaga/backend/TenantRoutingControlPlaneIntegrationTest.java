package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Execution(ExecutionMode.SAME_THREAD)
class TenantRoutingControlPlaneIntegrationTest extends TenantIsolationTestBase {
    private static final String DIRECTORY_TOKEN = "rafaga_test_directory_internal_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void internalDirectoryRequiresValidTokenAndAdminApisRequireSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/internal/tenant-directory"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/internal/tenant-directory")
                        .header("X-Tenant-Directory-Token", "wrong-token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/tenants/{tenantId}/domains", TENANT_A)
                        .with(jwtWithRole(TENANT_A, "STORE_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void newDomainActivatesViaVersionedDirectoryRefreshWithoutDisruptingExistingTenants() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String controlPlaneTenantId = "cp-tenant-" + suffix;
        String initialHost = "initial-" + suffix + ".controlplane.test";
        tenants.register(isolationTenant(controlPlaneTenantId, initialHost));

        String newHost = "store-" + suffix + ".controlplane.test";
        String frontendOrigin = "https://ui-" + suffix + ".controlplane.test";

        TenantDirectorySnapshot baseline = fetchDirectory(-1);
        assertThat(baseline.activeEntries())
                .extracting(entry -> entry.hostname())
                .contains(HOST_A, HOST_B, initialHost)
                .doesNotContain(newHost);

        // Request new domain: starts in REQUESTED, not yet routable or resolvable.
        mockMvc.perform(post("/api/admin/tenants/{tenantId}/domains", controlPlaneTenantId)
                        .with(superAdmin())
                        .header("Idempotency-Key", "req-" + newHost)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hostname", newHost,
                                "frontendOrigin", frontendOrigin))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostname").value(newHost))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.targetId").value("shared-pool"))
                .andExpect(jsonPath("$.targetType").value("SHARED_POOL"));

        assertThat(tenants.findByDomain(newHost)).isEmpty();
        assertThat(tenants.findByDomain(HOST_A)).isPresent();
        assertThat(tenants.findByDomain(HOST_B)).isPresent();

        // Direct REQUESTED -> ACTIVE is rejected with 409 Conflict.
        mockMvc.perform(post("/api/admin/tenants/{tenantId}/domains/{domain}/activate", controlPlaneTenantId, newHost)
                        .with(superAdmin()))
                .andExpect(status().isConflict());

        // Advance through lifecycle states.
        transition(controlPlaneTenantId, newHost, "OWNERSHIP_PENDING", "own-" + newHost);
        transition(controlPlaneTenantId, newHost, "CERTIFICATE_PENDING", "cert-" + newHost);
        transition(controlPlaneTenantId, newHost, "TARGET_PENDING", "target-" + newHost);

        long activatedVersion = activate(controlPlaneTenantId, newHost, "act-" + newHost);
        // Idempotent retry must publish at most one effective directory version.
        long retriedVersion = activate(controlPlaneTenantId, newHost, "act-" + newHost);
        assertThat(retriedVersion).isEqualTo(activatedVersion);
        assertThat(activatedVersion).isGreaterThan(baseline.directoryVersion());

        // Updating tenant metadata must NOT wipe control-plane domain state.
        mockMvc.perform(put("/api/admin/tenants/{tenantId}", controlPlaneTenantId)
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated Control Plane Tenant",
                                "domains", List.of(initialHost, newHost),
                                "deploymentMode", "SHARED_DB",
                                "planId", "test"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/tenants/{tenantId}/domains/{domain}", controlPlaneTenantId, newHost)
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.frontendOrigin").value(frontendOrigin))
                .andExpect(jsonPath("$.directoryVersion").value(activatedVersion));

        // Delta refresh exposes the newly active domain while pre-existing tenants remain intact.
        TenantDirectorySnapshot deltaAfterActivation = fetchDirectory(baseline.directoryVersion());
        assertThat(deltaAfterActivation.delta()).isTrue();
        assertThat(deltaAfterActivation.directoryVersion()).isEqualTo(activatedVersion);
        assertThat(deltaAfterActivation.activeEntries())
                .anySatisfy(entry -> {
                    assertThat(entry.hostname()).isEqualTo(newHost);
                    assertThat(entry.tenantId()).isEqualTo(controlPlaneTenantId);
                    assertThat(entry.frontendOrigin()).isEqualTo(frontendOrigin);
                    assertThat(entry.targetId()).isEqualTo("shared-pool");
                });
        assertThat(tenants.findByDomain(newHost)).isPresent();
        assertThat(tenants.findByDomain(HOST_B)).isPresent();

        // Suspending removes the domain in delta and blocks backend resolution.
        MvcResult suspendResult = mockMvc.perform(
                        post("/api/admin/tenants/{tenantId}/domains/{domain}/suspend", controlPlaneTenantId, newHost)
                                .with(superAdmin())
                                .header("Idempotency-Key", "susp-" + newHost))
                .andExpect(status().isOk())
                .andReturn();
        long suspendedVersion = objectMapper.readTree(suspendResult.getResponse().getContentAsString())
                .get("directoryVersion").asLong();
        assertThat(suspendedVersion).isGreaterThan(activatedVersion);

        TenantDirectorySnapshot deltaAfterSuspension = fetchDirectory(activatedVersion);
        assertThat(deltaAfterSuspension.removedHostnames()).contains(newHost);
        assertThat(deltaAfterSuspension.activeEntries())
                .extracting(entry -> entry.hostname())
                .doesNotContain(newHost);

        tenants.invalidateCache(controlPlaneTenantId);
        assertThat(tenants.findByDomain(newHost)).isEmpty();
        assertThat(tenants.findByDomain(HOST_B)).isPresent();
    }

    private TenantDirectorySnapshot fetchDirectory(long afterVersion) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/internal/tenant-directory")
                        .param("afterVersion", Long.toString(afterVersion))
                        .header("X-Tenant-Directory-Token", DIRECTORY_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TenantDirectorySnapshot.class);
    }

    private void transition(String tenantId, String domain, String targetStatus, String key) throws Exception {
        mockMvc.perform(post("/api/admin/tenants/{tenantId}/domains/{domain}/transition", tenantId, domain)
                        .with(superAdmin())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", targetStatus))))
                .andExpect(status().isOk());
    }

    private long activate(String tenantId, String domain, String key) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/tenants/{tenantId}/domains/{domain}/activate", tenantId, domain)
                                .with(superAdmin())
                                .header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("directoryVersion").asLong();
    }

    private RequestPostProcessor superAdmin() {
        return jwtWithRole(TENANT_A, "SUPER_ADMIN");
    }

    private RequestPostProcessor jwtWithRole(String tenantId, String role) {
        return jwt()
                .jwt(token -> token.claim("tenant_id", tenantId).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
