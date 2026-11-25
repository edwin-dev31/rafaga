package com.rafaga.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureFlagEnforcementIntegrationTest {
    private static final String TENANT_ID = "feature-disabled";
    private static final String HOST = "feature-disabled.rafaga.test";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantRegistry tenants;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void rejectsOperationsWhoseTenantFeatureIsDisabled() throws Exception {
        tenants.register(new TenantConfig(
                TENANT_ID,
                "Feature-disabled tenant",
                List.of(HOST),
                DeploymentMode.SHARED_DB,
                null,
                "test",
                TenantStatus.ACTIVE,
                Map.of("catalog", false, "inventory", true, "orders", true)));

        mvc.perform(post("/api/products")
                        .header("Host", HOST)
                        .with(jwt().jwt(token -> token.claim("tenant_id", TENANT_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_STORE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"DISABLED\",\"name\":\"Disabled\",\"basePrice\":10.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void allowsOrderReportForProPlanAndAfterUpgradeWhileReturningApiErrorForStarter() throws Exception {
        String host = "reports-" + UUID.randomUUID() + ".rafaga.test";
        var superAdmin = jwt().jwt(token -> token.subject("kc-super-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));

        MvcResult created = mvc.perform(post("/api/admin/tenants")
                        .with(superAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Starter Store",
                                  "domains": ["%s"],
                                  "deploymentMode": "SHARED_DB",
                                  "planId": "starter"
                                }
                                """.formatted(host)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.featureFlags.analytics").value(false))
                .andReturn();

        String tenantId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var storeManager = jwt().jwt(token -> token.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_STORE_MANAGER"));

        // Starter plan lacks analytics -> 403 with ApiError
        mvc.perform(get("/api/orders/report")
                        .header("Host", host)
                        .with(storeManager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));

        // Upgrade tenant from starter to pro -> analytics synced & Redis cache evicted
        mvc.perform(put("/api/admin/tenants/{tenantId}", tenantId)
                        .with(superAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pro Store",
                                  "domains": ["%s"],
                                  "deploymentMode": "SHARED_DB",
                                  "planId": "pro"
                                }
                                """.formatted(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureFlags.analytics").value(true));

        mvc.perform(get("/api/orders/report")
                        .header("Host", host)
                        .with(storeManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0));

        // Explicit admin override to false -> immediate 403
        mvc.perform(put("/api/admin/tenants/{tenantId}/features/analytics", tenantId)
                        .with(superAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureFlags.analytics").value(false));

        mvc.perform(get("/api/orders/report")
                        .header("Host", host)
                        .with(storeManager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));

        // Sync back with current plan (pro) -> 200 OK again
        mvc.perform(post("/api/admin/tenants/{tenantId}/features/sync", tenantId)
                        .with(superAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureFlags.analytics").value(true));

        mvc.perform(get("/api/orders/report")
                        .header("Host", host)
                        .with(storeManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0));
    }
}
