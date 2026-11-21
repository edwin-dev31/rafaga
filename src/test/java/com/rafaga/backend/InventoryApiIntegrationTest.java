package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.domain.event.OrderCancelledEvent;
import com.rafaga.backend.common.domain.event.OrderConfirmedEvent;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.inventory.domain.model.StockLevel;
import com.rafaga.backend.inventory.infrastructure.scheduling.ReservationExpiryScheduler;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryApiIntegrationTest extends TenantIsolationTestBase {
    private static final String TENANT_A = "inventory-test-a";
    private static final String TENANT_B = "inventory-test-b";
    private static final String HOST_A = "a.inventory.test";
    private static final String HOST_B = "b.inventory.test";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TenantRegistry tenants;
    @Autowired private StockService stock;
    @Autowired private ReservationExpiryScheduler scheduler;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;

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
    void initializesStockAndIsolatesQueriesAndAdjustments() throws Exception {
        UUID productId = createProduct();
        awaitStock(productId);

        mvc.perform(post("/api/stock/{productId}/adjust", productId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":10,\"notes\":\"Initial purchase\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(10))
                .andExpect(jsonPath("$.availableQuantity").value(10));
        mvc.perform(get("/api/stock/{productId}/movements", productId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[0].previousTotal").value(0));
        mvc.perform(get("/api/stock/{productId}", productId).header("Host", HOST_B)
                        .with(jwtFor(TENANT_B)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/stock/{productId}/adjust", productId).header("Host", HOST_B)
                        .with(jwtFor(TENANT_B)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1}"))
                .andExpect(status().isNotFound());

        TenantContext.setCurrentTenantId(TENANT_B);
        List<StockLevel> hidden = transactions.execute(status -> entityManager.createQuery(
                "select s from StockLevel s where s.productId = :productId", StockLevel.class)
                .setParameter("productId", productId).getResultList());
        assertThat(hidden).isEmpty();
        TenantContext.setCurrentTenantId(TENANT_A);
        List<StockLevel> visible = transactions.execute(status -> entityManager.createQuery(
                "select s from StockLevel s where s.productId = :productId", StockLevel.class)
                .setParameter("productId", productId).getResultList());
        assertThat(visible).hasSize(1);
    }

    @Test
    void reservesReleasesConfirmsAndExpiresStock() throws Exception {
        UUID productId = createProduct();
        awaitStock(productId);
        TenantContext.setCurrentTenantId(TENANT_A);
        stock.adjustStock(productId, null, 10, "Initial purchase");
        String reference = "order-" + UUID.randomUUID();
        ReservationView first = stock.reserveStock(productId, null, 4, reference, null);
        assertThat(stock.getStockLevel(productId, null).availableQuantity()).isEqualTo(6);
        stock.releaseReservation(first.id());
        assertThat(stock.getStockLevel(productId, null).availableQuantity()).isEqualTo(10);

        ReservationView second = stock.reserveStock(productId, null, 3,
                "order-" + UUID.randomUUID(), null);
        stock.confirmReservation(second.id());
        assertThat(stock.getStockLevel(productId, null).totalQuantity()).isEqualTo(7);
        assertThat(stock.getStockLevel(productId, null).reservedQuantity()).isZero();

        ReservationView expiring = stock.reserveStock(productId, null, 2,
                "order-" + UUID.randomUUID(), Instant.now().minusSeconds(5));
        TenantContext.clear();
        scheduler.expireReservationsImmediately();
        TenantContext.setCurrentTenantId(TENANT_A);
        assertThat(stock.getStockLevel(productId, null).availableQuantity()).isEqualTo(7);
        assertThat(stock.getActiveReservations(expiring.referenceId())).isEmpty();
    }

    @Test
    void orderEventsReserveAndReleaseStockForTheirTenant() throws Exception {
        UUID productId = createProduct();
        awaitStock(productId);
        TenantContext.setCurrentTenantId(TENANT_A);
        stock.adjustStock(productId, null, 8, "Initial purchase");
        TenantContext.clear();

        UUID orderId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> events.publishEvent(new OrderConfirmedEvent(
                orderId, TENANT_A, List.of(new OrderConfirmedEvent.Line(productId, null, 3)))));
        awaitReservationCount(orderId, 1);
        TenantContext.setCurrentTenantId(TENANT_A);
        assertThat(stock.getStockLevel(productId, null).availableQuantity()).isEqualTo(5);
        TenantContext.clear();

        transactions.executeWithoutResult(status -> events.publishEvent(new OrderCancelledEvent(orderId, TENANT_A)));
        awaitReservationCount(orderId, 0);
        TenantContext.setCurrentTenantId(TENANT_A);
        assertThat(stock.getStockLevel(productId, null).availableQuantity()).isEqualTo(8);
    }

    private UUID createProduct() throws Exception {
        MvcResult result = mvc.perform(post("/api/products").header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"INV-" + UUID.randomUUID()
                                + "\",\"name\":\"Test product\",\"basePrice\":1}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private void awaitStock(UUID productId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            int status = mvc.perform(get("/api/stock/{productId}", productId)
                            .header("Host", HOST_A).with(jwtFor(TENANT_A)))
                    .andReturn().getResponse().getStatus();
            if (status == 200) return;
            Thread.sleep(100);
        }
        mvc.perform(get("/api/stock/{productId}", productId).header("Host", HOST_A)
                        .with(jwtFor(TENANT_A)))
                .andExpect(status().isOk());
    }

    private void awaitReservationCount(UUID orderId, int count) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            TenantContext.setCurrentTenantId(TENANT_A);
            int actual = stock.getActiveReservations(orderId.toString()).size();
            TenantContext.clear();
            if (actual == count) return;
            Thread.sleep(100);
        }
        TenantContext.setCurrentTenantId(TENANT_A);
        assertThat(stock.getActiveReservations(orderId.toString())).hasSize(count);
        TenantContext.clear();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String tenantId) {
        return jwt().jwt(token -> token.claim("tenant_id", tenantId))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_STORE_MANAGER"));
    }

    private TenantConfig tenant(String id, String host) {
        return new TenantConfig(id, id, List.of(host), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of(
                        "catalog", true,
                        "inventory", true));
    }
}
