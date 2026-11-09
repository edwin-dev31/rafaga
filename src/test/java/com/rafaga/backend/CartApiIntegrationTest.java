package com.rafaga.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Instant;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartApiIntegrationTest {
    private static final String TENANT_A = "cart-test-a";
    private static final String TENANT_B = "cart-test-b";
    private static final String HOST_A = "a.cart.test";
    private static final String HOST_B = "b.cart.test";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRegistry tenants;
    @Autowired private IdentityStore identities;

    @BeforeEach
    void registerTenants() {
        tenants.register(tenant(TENANT_A, HOST_A));
        tenants.register(tenant(TENANT_B, HOST_B));
        tenants.findById(TENANT_A);
        tenants.findById(TENANT_B);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsPricesAndChecksOutTheActiveCart() throws Exception {
        Customer customer = createCustomer(TENANT_A, "cart-owner");
        ProductFixture product = createProductWithVariant(customer);

        MvcResult created = mvc.perform(get("/api/carts").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn();
        String cartId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(put("/api/carts/items").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A, customer.keycloakId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(customer, product, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.50))
                .andExpect(jsonPath("$.total").value(25.00));

        mvc.perform(get("/api/carts/{cartId}", cartId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId))
                .andExpect(jsonPath("$.total").value(25.00));

        mvc.perform(post("/api/carts/checkout").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.checkedOutAt").isNotEmpty());

        MvcResult next = mvc.perform(get("/api/carts").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn();
        String nextCartId = mapper.readTree(next.getResponse().getContentAsString()).path("id").asText();
        org.assertj.core.api.Assertions.assertThat(nextCartId).isNotEqualTo(cartId);
    }

    @Test
    void hidesCartsFromOtherTenantsAndCustomers() throws Exception {
        Customer owner = createCustomer(TENANT_A, "cart-owner");
        Customer otherCustomer = createCustomer(TENANT_A, "cart-other");
        Customer foreignCustomer = createCustomer(TENANT_B, "cart-foreign");
        ProductFixture product = createProductWithVariant(owner);

        String cartId = upsertItem(owner, product, 1);

        mvc.perform(get("/api/carts").param("customerId", owner.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, otherCustomer.keycloakId())))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/carts/{cartId}", cartId).header("Host", HOST_B)
                        .with(jwtFor(TENANT_B, foreignCustomer.keycloakId())))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/carts/items").header("Host", HOST_B)
                        .with(jwtFor(TENANT_B, foreignCustomer.keycloakId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(foreignCustomer, product, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void abandonsCartAndRejectsInvalidOperations() throws Exception {
        Customer customer = createCustomer(TENANT_A, "cart-abandon");
        ProductFixture product = createProductWithVariant(customer);

        mvc.perform(get("/api/carts").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/carts/checkout").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/carts/items").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A, customer.keycloakId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(customer, product, 0)))
                .andExpect(status().isBadRequest());

        upsertItem(customer, product, 1);

        mvc.perform(delete("/api/carts").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/carts").param("customerId", customer.id().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A, customer.keycloakId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private String upsertItem(Customer customer, ProductFixture product, int quantity) throws Exception {
        MvcResult result = mvc.perform(put("/api/carts/items").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A, customer.keycloakId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(customer, product, quantity)))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private String itemBody(Customer customer, ProductFixture product, int quantity) {
        return "{\"customerId\":\"" + customer.id() + "\",\"productId\":\"" + product.id()
                + "\",\"variantId\":\"" + product.variantId() + "\",\"quantity\":" + quantity + "}";
    }

    private ProductFixture createProductWithVariant(Customer customer) throws Exception {
        MvcResult result = mvc.perform(post("/api/products").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A, customer.keycloakId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CART-" + UUID.randomUUID()
                                + "\",\"name\":\"Cart test product\",\"basePrice\":10.00,"
                                + "\"variants\":[{\"sku\":\"CVAR-" + UUID.randomUUID()
                                + "\",\"name\":\"Large\",\"priceAdjustment\":2.50}]}"))
                .andExpect(status().isCreated()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        return new ProductFixture(UUID.fromString(json.path("id").asText()),
                UUID.fromString(json.path("variants").get(0).path("id").asText()));
    }

    private Customer createCustomer(String tenantId, String prefix) {
        String suffix = UUID.randomUUID().toString();
        TenantContext.setCurrentTenantId(tenantId);
        try {
            User user = identities.saveUser(new User(tenantId, prefix + "-" + suffix,
                    prefix + "-" + suffix, prefix + "-" + suffix + "@example.test", Instant.now()));
            return new Customer(user.getId(), user.getKeycloakId());
        } finally {
            TenantContext.clear();
        }
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(
            String tenantId, String keycloakId) {
        return jwt().jwt(token -> token.subject(keycloakId).claim("tenant_id", tenantId));
    }

    private TenantConfig tenant(String id, String host) {
        return new TenantConfig(id, id, List.of(host), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of("cart", true));
    }

    private record Customer(UUID id, String keycloakId) { }

    private record ProductFixture(UUID id, UUID variantId) { }
}
