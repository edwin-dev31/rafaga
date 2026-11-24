package com.rafaga.backend.infrastructure.web.storefront;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.orders.application.service.OrderService;
import com.rafaga.backend.orders.application.service.OrderView;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StorefrontOrderReceiptControllerTest {

    private OrderService orders;
    private ProductService products;
    private TenantRegistry tenants;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        orders = mock(OrderService.class);
        products = mock(ProductService.class);
        tenants = mock(TenantRegistry.class);
        mvc = MockMvcBuilders.standaloneSetup(new StorefrontOrderReceiptController(orders, products, tenants)).build();
        TenantContext.setCurrentTenantId("tienda-norte");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsReadOnlyReceiptWithNoStoreAndNoIndexHeadersForValidToken() throws Exception {
        UUID orderId = UUID.fromString("12345678-1111-2222-3333-444444444444");
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        String token = "unguessable-public-token-123";
        Instant now = Instant.parse("2026-05-01T10:00:00Z");

        OrderView order = new OrderView(
                orderId, null, UUID.randomUUID(), token, OrderStatus.PENDING,
                new BigDecimal("50.00"), new BigDecimal("50.00"),
                List.of(new OrderView.LineView(UUID.randomUUID(), productId, variantId, 2,
                        new BigDecimal("25.00"), new BigDecimal("50.00"))),
                now, now, null, null);
        ProductView product = new ProductView(
                productId, "CAM-01", "Camiseta Rafaga", "desc", new BigDecimal("20.00"),
                ProductStatus.ACTIVE, Set.of(),
                List.of(new ProductView.VariantView(variantId, "CAM-01-L", "Talla L",
                        Map.of("talla", "L"), new BigDecimal("5.00"), VariantStatus.ACTIVE)),
                now, now);

        when(orders.findOrderByPublicToken(token)).thenReturn(Optional.of(order));
        when(products.getProductById(productId)).thenReturn(product);
        when(tenants.findById("tienda-norte")).thenReturn(Optional.of(new TenantConfig(
                "tienda-norte", "Tienda Norte", List.of("norte.rafaga.app"),
                DeploymentMode.SHARED_DB, null, "pro", TenantStatus.ACTIVE, Map.of())));

        mvc.perform(get("/api/storefront/orders/{publicToken}", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(jsonPath("$.storeName").value("Tienda Norte"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.reference").value("RFG-12345678"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(50.00))
                .andExpect(jsonPath("$.items[0].productName").value("Camiseta Rafaga"))
                .andExpect(jsonPath("$.items[0].variantName").value("Talla L"))
                .andExpect(jsonPath("$.items[0].sku").value("CAM-01-L"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownOrCrossTenantToken() throws Exception {
        when(orders.findOrderByPublicToken("wrong-token")).thenReturn(Optional.empty());

        mvc.perform(get("/api/storefront/orders/{publicToken}", "wrong-token"))
                .andExpect(status().isNotFound());
    }
}
