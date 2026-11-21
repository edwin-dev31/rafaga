package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.port.CloudflareHostnamePort;
import com.rafaga.backend.tenant.application.port.CustomHostnameStatus;
import com.rafaga.backend.tenant.application.port.R2CorsPort;
import com.rafaga.backend.tenant.application.service.DomainActivationWorker;
import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = "rafaga.web.allowed-origins=https://app.rafaga.app")
class CloudflareProvisioningContractIntegrationTest extends TenantIsolationTestBase {
    private static final String DIRECTORY_TOKEN = "rafaga_test_directory_internal_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainActivationWorker worker;

    @MockitoBean
    private CloudflareHostnamePort fakeCloudflare;

    @MockitoBean
    private R2CorsPort fakeR2Cors;

    @Test
    void activatesDomainThroughWorkerSyncsAndRollsBackR2CorsAndRemovesCleanly() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenantId = "cf-tenant-" + suffix;
        String initialHost = "init-" + suffix + ".cf.test";
        String storeHost = "store-" + suffix + ".cf.test";
        String frontendOrigin = "https://ui-" + suffix + ".cf.test";
        String externalId = "cf-ext-" + suffix;

        tenants.register(isolationTenant(tenantId, initialHost));

        AtomicBoolean ownershipReady = new AtomicBoolean(false);
        AtomicBoolean certReady = new AtomicBoolean(false);
        AtomicBoolean deleted = new AtomicBoolean(false);
        List<List<String>> appliedCorsHistory = new ArrayList<>();

        when(fakeCloudflare.createOrGetCustomHostname(storeHost)).thenReturn(
                new CustomHostnameStatus(externalId, storeHost, false, false, false, null, "_cf." + storeHost, "val"));
        when(fakeCloudflare.getCustomHostname(externalId, storeHost)).thenAnswer(inv -> Optional.of(
                new CustomHostnameStatus(externalId, storeHost, ownershipReady.get(), certReady.get(), false, null, null, null)));
        doAnswer(inv -> {
            deleted.set(true);
            return null;
        }).when(fakeCloudflare).deleteCustomHostname(externalId);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> origins = (List<String>) inv.getArgument(0);
            appliedCorsHistory.add(List.copyOf(origins));
            return null;
        }).when(fakeR2Cors).applyAllowedOrigins(org.mockito.ArgumentMatchers.anyList());

        // 1. Request domain via super-admin API
        mockMvc.perform(post("/api/admin/tenants/{tenantId}/domains", tenantId)
                        .with(superAdmin())
                        .header("Idempotency-Key", "req-" + storeHost)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hostname", storeHost,
                                "frontendOrigin", frontendOrigin))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        // 2. Worker creates Cloudflare custom hostname -> OWNERSHIP_PENDING
        worker.processDueDomains();
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/domains/{domain}", tenantId, storeHost)
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OWNERSHIP_PENDING"));

        // 3. Ownership verified -> CERTIFICATE_PENDING
        ownershipReady.set(true);
        worker.processDueDomains();
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/domains/{domain}", tenantId, storeHost)
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CERTIFICATE_PENDING"));

        // 4. Certificate ready -> TARGET_PENDING -> ACTIVE
        certReady.set(true);
        worker.processDueDomains();
        worker.processDueDomains();
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/domains/{domain}", tenantId, storeHost)
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify directory exposes newly activated domain
        MvcResult dirResult = mockMvc.perform(get("/api/internal/tenant-directory")
                        .param("afterVersion", "-1")
                        .header("X-Tenant-Directory-Token", DIRECTORY_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        TenantDirectorySnapshot snapshot = objectMapper.readValue(
                dirResult.getResponse().getContentAsString(), TenantDirectorySnapshot.class);
        assertThat(snapshot.activeEntries())
                .extracting(entry -> entry.hostname())
                .contains(storeHost);

        // 5. Explicit R2 CORS sync (v1) + second sync (v2) + rollback (v3 -> v1)
        mockMvc.perform(post("/api/admin/media/r2-cors/sync")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "additionalOrigins", List.of("https://app.rafaga.app")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(post("/api/admin/media/r2-cors/sync")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "additionalOrigins", List.of("https://app.rafaga.app", "https://extra.rafaga.app")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(post("/api/admin/media/r2-cors/rollback")
                        .with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        assertThat(appliedCorsHistory).hasSize(3);
        assertThat(appliedCorsHistory.get(0)).contains("https://app.rafaga.app", frontendOrigin);
        assertThat(appliedCorsHistory.get(1)).contains("https://extra.rafaga.app");
        assertThat(appliedCorsHistory.get(2)).isEqualTo(appliedCorsHistory.get(0));

        // 6. Wildcard origin is rejected with 400
        mockMvc.perform(post("/api/admin/media/r2-cors/sync")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "additionalOrigins", List.of("https://*.rafaga.app")))))
                .andExpect(status().isBadRequest());

        // 7. Remove domain -> worker deletes external hostname
        mockMvc.perform(delete("/api/admin/tenants/{tenantId}/domains/{domain}", tenantId, storeHost)
                        .with(superAdmin()))
                .andExpect(status().isAccepted());
        worker.processDueDomains();
        assertThat(deleted.get()).isTrue();
    }

    private RequestPostProcessor superAdmin() {
        return jwt()
                .jwt(token -> token.claim("tenant_id", TENANT_A).claim("roles", List.of("SUPER_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }
}
