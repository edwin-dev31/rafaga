package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end test verifying multi-tenant isolation through the public storefront API.
 * 
 * Scenario:
 * 1. Create 2 tenants (shops) with separate domains.
 * 2. Register one product in each tenant via the authenticated admin API.
 * 3. Verify that the storefront API returns only the products belonging to the resolved tenant.
 * 4. Confirm that Tenant A cannot see Tenant B's products and vice versa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontMultiTenantIsolationTest {

    private String tenantAId;
    private String tenantADomain;
    private String tenantBId;
    private String tenantBDomain;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TenantRegistry tenants;

    @BeforeEach
    void registerTenants() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        tenantAId = "shop-a-" + uuid;
        tenantADomain = "alpha-" + uuid + ".shop.test";
        tenantBId = "shop-b-" + uuid;
        tenantBDomain = "beta-" + uuid + ".shop.test";
        tenants.register(tenant(tenantAId, tenantADomain));
        tenants.register(tenant(tenantBId, tenantBDomain));
        tenants.findById(tenantAId);
        tenants.findById(tenantBId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void eachTenantSeesOnlyItsOwnProductsViaStorefront() throws Exception {
        // 1. Create a product in Tenant A (created as DRAFT, then activate)
        String skuA = "ALPHA-" + UUID.randomUUID();
        MvcResult createdA = mvc.perform(post("/api/products")
                        .header("Host", tenantADomain)
                        .with(jwtFor(tenantAId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + skuA + "\",\"name\":\"Alpha Coffee\"," 
                                + "\"basePrice\":12.50}"))
                .andExpect(status().isCreated())
                .andReturn();
        String productIdA = mapper.readTree(createdA.getResponse().getContentAsString())
                .path("id").asText();
        mvc.perform(put("/api/products/{productId}", productIdA)
                        .header("Host", tenantADomain)
                        .with(jwtFor(tenantAId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alpha Coffee\",\"basePrice\":12.50,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        // 2. Create a product in Tenant B (created as DRAFT, then activate)
        String skuB = "BETA-" + UUID.randomUUID();
        MvcResult createdB = mvc.perform(post("/api/products")
                        .header("Host", tenantBDomain)
                        .with(jwtFor(tenantBId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + skuB + "\",\"name\":\"Beta Tea\"," 
                                + "\"basePrice\":8.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        String productIdB = mapper.readTree(createdB.getResponse().getContentAsString())
                .path("id").asText();
        mvc.perform(put("/api/products/{productId}", productIdB)
                        .header("Host", tenantBDomain)
                        .with(jwtFor(tenantBId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beta Tea\",\"basePrice\":8.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        // 3. Verify Tenant A's storefront only shows Alpha Coffee
        MvcResult storefrontA = mvc.perform(get("/api/storefront/products")
                        .header("Host", tenantADomain))
                .andExpect(status().isOk())
                .andReturn();

        var contentA = mapper.readTree(storefrontA.getResponse().getContentAsString())
                .path("content");
        assertThat(contentA).extracting(json -> json.path("id").asText())
                .contains(productIdA)
                .doesNotContain(productIdB);

        // 4. Verify Tenant B's storefront only shows Beta Tea
        MvcResult storefrontB = mvc.perform(get("/api/storefront/products")
                        .header("Host", tenantBDomain))
                .andExpect(status().isOk())
                .andReturn();

        var contentB = mapper.readTree(storefrontB.getResponse().getContentAsString())
                .path("content");
        assertThat(contentB).extracting(json -> json.path("id").asText())
                .contains(productIdB)
                .doesNotContain(productIdA);

        // 5. Verify individual product endpoint also respects tenant isolation
        mvc.perform(get("/api/storefront/products/" + productIdA)
                        .header("Host", tenantBDomain))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/storefront/products/" + productIdB)
                        .header("Host", tenantADomain))
                .andExpect(status().isNotFound());

        // 6. Verify the correct tenant can access its own product by ID
        mvc.perform(get("/api/storefront/products/" + productIdA)
                        .header("Host", tenantADomain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Coffee"));

        mvc.perform(get("/api/storefront/products/" + productIdB)
                        .header("Host", tenantBDomain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta Tea"));
    }

    @Test
    void storefrontDoesNotExposeInactiveProducts() throws Exception {
        // Create an ACTIVE product (create as DRAFT, then activate)
        String skuActive = "ACTIVE-" + UUID.randomUUID();
        MvcResult activeResult = mvc.perform(post("/api/products")
                        .header("Host", tenantADomain)
                        .with(jwtFor(tenantAId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + skuActive + "\",\"name\":\"Visible Product\"," 
                                + "\"basePrice\":10.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        String activeId = mapper.readTree(activeResult.getResponse().getContentAsString())
                .path("id").asText();
        mvc.perform(put("/api/products/{productId}", activeId)
                        .header("Host", tenantADomain)
                        .with(jwtFor(tenantAId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Visible Product\",\"basePrice\":10.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        // Create an ARCHIVED product (create as DRAFT, then set to ARCHIVED)
        String skuArchived = "ARCHIVED-" + UUID.randomUUID();
        MvcResult archivedResult = mvc.perform(post("/api/products")
                        .header("Host", tenantADomain)
                        .with(jwtFor(tenantAId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + skuArchived + "\",\"name\":\"Hidden Product\"," 
                                + "\"basePrice\":5.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        String archivedId = mapper.readTree(archivedResult.getResponse().getContentAsString())
                .path("id").asText();
        mvc.perform(put("/api/products/{productId}", archivedId)
                        .header("Host", tenantADomain)
                        .with(jwtFor(tenantAId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hidden Product\",\"basePrice\":5.00,\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());

        // Storefront should only show the ACTIVE product
        MvcResult result = mvc.perform(get("/api/storefront/products")
                        .header("Host", tenantADomain))
                .andExpect(status().isOk())
                .andReturn();
        var content = mapper.readTree(result.getResponse().getContentAsString())
                .path("content");
        assertThat(content).extracting(json -> json.path("name").asText())
                .contains("Visible Product")
                .doesNotContain("Hidden Product");
    }

    @Test
    void storefrontRequiresValidTenantDomain() throws Exception {
        // Request with an unknown domain should return 400
        mvc.perform(get("/api/storefront/products")
                        .header("Host", "unknown-" + UUID.randomUUID() + ".domain.test"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String tenantId) {
        return jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId));
    }

    private TenantConfig tenant(String id, String domain) {
        return new TenantConfig(id, id, List.of(domain), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of("catalog", true));
    }
}
