package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiIntegrationTest extends TenantIsolationTestBase {
    private static final String TENANT_A = "catalog-test-a";
    private static final String TENANT_B = "catalog-test-b";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRegistry tenants;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void registerTenants() {
        tenants.register(tenant(TENANT_A, "a.catalog.test"));
        tenants.register(tenant(TENANT_B, "b.catalog.test"));
        tenants.findById(TENANT_A);
        tenants.findById(TENANT_B);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsUpdatesSearchesAndDeletesProductsWithCategoriesAndVariants() throws Exception {
        String rootId = createCategory(TENANT_A, "a.catalog.test", "Root", "root-" + UUID.randomUUID(), null);
        String childId = createCategory(TENANT_A, "a.catalog.test", "Child", "child-" + UUID.randomUUID(), rootId);
        mvc.perform(put("/api/categories/{categoryId}", childId).header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Child Updated\",\"slug\":\"child-updated-" + UUID.randomUUID()
                                + "\",\"parentId\":\"" + rootId + "\",\"displayOrder\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Child Updated"));
        mvc.perform(get("/api/categories").header("Host", "a.catalog.test").with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + rootId + "')].children[0].id").value(hasItem(childId)));

        String sku = "SKU-" + UUID.randomUUID();
        MvcResult created = mvc.perform(post("/api/products")
                        .header("Host", "a.catalog.test").with(jwtFor(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Coffee\",\"basePrice\":12.50,"
                                + "\"categoryIds\":[\"" + childId + "\"],\"variants\":[{\"sku\":\"VAR-"
                                + UUID.randomUUID() + "\",\"name\":\"Large\",\"attributes\":{\"size\":\"L\"},"
                                + "\"priceAdjustment\":2.00}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants[0].attributes.size").value("L"))
                .andReturn();
        String productId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(get("/api/products/{productId}", productId).header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Coffee"));
        mvc.perform(get("/api/products?page=0&size=100").header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id").value(hasItem(productId)));
        mvc.perform(put("/api/products/{productId}", productId).header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coffee Premium\",\"basePrice\":15.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Coffee Premium"));
        mvc.perform(delete("/api/products/{productId}", productId).header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/{productId}", productId).header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesOtherTenantProductsAndRejectsJwtDomainMismatch() throws Exception {
        String sku = "SKU-" + UUID.randomUUID();
        MvcResult created = mvc.perform(post("/api/products")
                        .header("Host", "a.catalog.test").with(jwtFor(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Private\",\"basePrice\":1}"))
                .andExpect(status().isCreated()).andReturn();
        String productId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(get("/api/products/{productId}", productId).header("Host", "b.catalog.test")
                        .with(jwtFor(TENANT_B)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/products/{productId}", productId).header("Host", "b.catalog.test")
                        .with(jwtFor(TENANT_B)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tampered\",\"basePrice\":2,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/{productId}", productId).header("Host", "b.catalog.test")
                        .with(jwtFor(TENANT_B)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{productId}", productId).header("Host", "b.catalog.test")
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/{productId}", productId).header("Host", "a.catalog.test")
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Private"));

        TenantContext.setCurrentTenantId(TENANT_B);
        List<Product> visibleToB = transactions.execute(status -> entityManager
                .createQuery("select p from Product p where p.sku = :sku", Product.class)
                .setParameter("sku", sku).getResultList());
        assertThat(visibleToB).isEmpty();
        TenantContext.setCurrentTenantId(TENANT_A);
        List<Product> visibleToA = transactions.execute(status -> entityManager
                .createQuery("select p from Product p where p.sku = :sku", Product.class)
                .setParameter("sku", sku).getResultList());
        assertThat(visibleToA).hasSize(1);
    }

    @Test
    void paginatesProductsWithinOneTenant() throws Exception {
        String tenantId = "page-" + UUID.randomUUID().toString().substring(0, 16);
        String host = "page-" + UUID.randomUUID() + ".catalog.test";
        tenants.register(tenant(tenantId, host));
        tenants.findById(tenantId);
        for (String name : List.of("Alpha", "Beta")) {
            mvc.perform(post("/api/products").header("Host", host).with(jwtFor(tenantId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sku\":\"SKU-" + UUID.randomUUID() + "\",\"name\":\""
                                    + name + "\",\"basePrice\":1}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/products?page=0&size=1&sort=name,asc").header("Host", host)
                        .with(jwtFor(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Alpha"));
        mvc.perform(get("/api/products?page=1&size=1&sort=name,asc").header("Host", host)
                        .with(jwtFor(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Beta"));
    }

    private String createCategory(String tenantId, String host, String name,
            String slug, String parentId) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"slug\":\"" + slug
                + "\",\"displayOrder\":0" + (parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"") + "}";
        MvcResult response = mvc.perform(post("/api/categories").header("Host", host)
                        .with(jwtFor(tenantId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("id").asText();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String tenantId) {
        return jwt().jwt(jwt -> jwt.claim("tenant_id", tenantId));
    }

    private TenantConfig tenant(String id, String domain) {
        return new TenantConfig(id, id, List.of(domain), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of("catalog", true));
    }
}
