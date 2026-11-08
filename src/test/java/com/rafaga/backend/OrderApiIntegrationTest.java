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

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.inventory.domain.model.ReservationStatus;
import com.rafaga.backend.orders.domain.model.Order;
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
class OrderApiIntegrationTest {
    private static final String TENANT_A = "orders-test-a";
    private static final String TENANT_B = "orders-test-b";
    private static final String HOST_A = "a.orders.test";
    private static final String HOST_B = "b.orders.test";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRegistry tenants;
    @Autowired private StockService stock;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;

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
    void createsPricedOrderReservesStockAndHidesItFromOtherTenants() throws Exception {
        ProductFixture product = createProductWithVariant();
        awaitStock(product.id(), product.variantId());
        adjust(product.id(), product.variantId(), 10);
        UUID orderId = createOrder(TENANT_A, HOST_A, product.id(), product.variantId(), 2);

        mvc.perform(get("/api/orders/{orderId}", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(25.00))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(12.50))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(25.00));
        mvc.perform(get("/api/stock/{productId}", product.id()).param("variantId", product.variantId().toString())
                        .header("Host", HOST_A).with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(8))
                .andExpect(jsonPath("$.reservedQuantity").value(2));
        mvc.perform(get("/api/orders?page=0&size=100").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(orderId.toString())));
        mvc.perform(get("/api/orders/{orderId}", orderId).header("Host", HOST_B)
                        .with(jwtFor(TENANT_B)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/orders/{orderId}/status", orderId).header("Host", HOST_B)
                        .with(jwtFor(TENANT_B)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/orders/{orderId}", orderId).header("Host", HOST_B)
                        .with(jwtFor(TENANT_B)))
                .andExpect(status().isNotFound());

        TenantContext.setCurrentTenantId(TENANT_B);
        List<Order> hidden = transactions.execute(status -> entityManager.createQuery(
                "select o from Order o where o.id = :id", Order.class)
                .setParameter("id", orderId).getResultList());
        assertThat(hidden).isEmpty();
        TenantContext.setCurrentTenantId(TENANT_A);
        List<Order> visible = transactions.execute(status -> entityManager.createQuery(
                "select o from Order o where o.id = :id", Order.class)
                .setParameter("id", orderId).getResultList());
        assertThat(visible).hasSize(1);
    }

    @Test
    void confirmsCompletesAndRejectsCancellationAfterCompletion() throws Exception {
        UUID productId = createProduct();
        awaitStock(productId, null);
        adjust(productId, null, 10);
        UUID orderId = createOrder(TENANT_A, HOST_A, productId, null, 3);

        mvc.perform(put("/api/orders/{orderId}/status", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(put("/api/orders/{orderId}/status", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/stock/{productId}", productId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
        TenantContext.setCurrentTenantId(TENANT_A);
        assertThat(stock.reserveStock(productId, null, 3, orderId.toString(), null).status())
                .isEqualTo(ReservationStatus.CONFIRMED);
        TenantContext.clear();
        mvc.perform(delete("/api/orders/{orderId}", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelsOrderAndReleasesReservedStock() throws Exception {
        UUID productId = createProduct();
        awaitStock(productId, null);
        adjust(productId, null, 10);
        UUID orderId = createOrder(TENANT_A, HOST_A, productId, null, 4);

        mvc.perform(delete("/api/orders/{orderId}", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/orders/{orderId}", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/stock/{productId}", productId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
        TenantContext.setCurrentTenantId(TENANT_A);
        assertThat(stock.reserveStock(productId, null, 4, orderId.toString(), null).status())
                .isEqualTo(ReservationStatus.RELEASED);
        TenantContext.clear();
        mvc.perform(delete("/api/orders/{orderId}", orderId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingProductsAndRollsBackReservationsWhenAnotherLineLacksStock() throws Exception {
        UUID absentProduct = UUID.randomUUID();
        mvc.perform(post("/api/orders").header("Host", HOST_A).with(jwtFor(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(absentProduct, null, 1)))
                .andExpect(status().isNotFound());

        UUID first = createProduct();
        UUID second = createProduct();
        awaitStock(first, null);
        awaitStock(second, null);
        adjust(first, null, 5);
        String body = "{\"customerId\":\"" + UUID.randomUUID() + "\",\"lines\":["
                + "{\"productId\":\"" + first + "\",\"quantity\":2},"
                + "{\"productId\":\"" + second + "\",\"quantity\":1}]}";
        mvc.perform(post("/api/orders").header("Host", HOST_A).with(jwtFor(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/stock/{productId}", first).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(5))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
        mvc.perform(post("/api/orders").header("Host", HOST_B).with(jwtFor(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(first, null, 1)))
                .andExpect(status().isNotFound());
    }

    private UUID createProduct() throws Exception {
        MvcResult result = mvc.perform(post("/api/products").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ORD-" + UUID.randomUUID()
                                + "\",\"name\":\"Order test product\",\"basePrice\":10.00}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private ProductFixture createProductWithVariant() throws Exception {
        MvcResult result = mvc.perform(post("/api/products").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ORD-" + UUID.randomUUID()
                                + "\",\"name\":\"Variant product\",\"basePrice\":10.00,"
                                + "\"variants\":[{\"sku\":\"VAR-" + UUID.randomUUID()
                                + "\",\"name\":\"Large\",\"priceAdjustment\":2.50}]}"))
                .andExpect(status().isCreated()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        return new ProductFixture(UUID.fromString(json.path("id").asText()),
                UUID.fromString(json.path("variants").get(0).path("id").asText()));
    }

    private UUID createOrder(String tenantId, String host, UUID productId, UUID variantId, int quantity)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/orders").header("Host", host)
                        .with(jwtFor(tenantId)).contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, variantId, quantity)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private String orderBody(UUID productId, UUID variantId, int quantity) {
        return "{\"customerId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"productId\":\""
                + productId + "\",\"quantity\":" + quantity
                + (variantId == null ? "" : ",\"variantId\":\"" + variantId + "\"") + "}]}";
    }

    private void adjust(UUID productId, UUID variantId, int delta) {
        TenantContext.setCurrentTenantId(TENANT_A);
        try {
            stock.adjustStock(productId, variantId, delta, "Order test stock");
        } finally {
            TenantContext.clear();
        }
    }

    private void awaitStock(UUID productId, UUID variantId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            var request = get("/api/stock/{productId}", productId).header("Host", HOST_A)
                    .with(jwtFor(TENANT_A));
            if (variantId != null) request.param("variantId", variantId.toString());
            if (mvc.perform(request).andReturn().getResponse().getStatus() == 200) return;
            Thread.sleep(100);
        }
        var request = get("/api/stock/{productId}", productId).header("Host", HOST_A)
                .with(jwtFor(TENANT_A));
        if (variantId != null) request.param("variantId", variantId.toString());
        mvc.perform(request).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String tenantId) {
        return jwt().jwt(token -> token.claim("tenant_id", tenantId));
    }

    private TenantConfig tenant(String id, String host) {
        return new TenantConfig(id, id, List.of(host), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of("orders", true));
    }

    private record ProductFixture(UUID id, UUID variantId) { }
}
