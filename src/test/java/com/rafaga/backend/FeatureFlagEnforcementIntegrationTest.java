package com.rafaga.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(status().isForbidden());
    }
}
