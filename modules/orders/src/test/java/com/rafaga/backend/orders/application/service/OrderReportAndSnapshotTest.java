package com.rafaga.backend.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.inventory.domain.model.ReservationStatus;
import com.rafaga.backend.orders.application.command.CreateOrder;
import com.rafaga.backend.orders.application.port.OrderSearchFilter;
import com.rafaga.backend.orders.application.port.OrderStore;
import com.rafaga.backend.orders.domain.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class OrderReportAndSnapshotTest {
    private static final String TENANT = "tenant-a";

    private OrderStore store;
    private ProductService products;
    private StockService stock;
    private OrderService service;

    @BeforeEach
    void setUp() {
        store = mock(OrderStore.class);
        products = mock(ProductService.class);
        stock = mock(StockService.class);
        service = new OrderService(store, products, stock, mock(ApplicationEventPublisher.class));
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void persistsProductAndVariantSnapshotOnOrderLines() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        when(products.getProductById(productId)).thenReturn(new ProductView(
                productId, "CAM-01", "Camiseta Rafaga", "desc", new BigDecimal("20.00"),
                ProductStatus.ACTIVE, Set.of(),
                List.of(new ProductView.VariantView(variantId, "CAM-01-M", "Talla M",
                        Map.of("talla", "M"), new BigDecimal("5.00"), VariantStatus.ACTIVE)),
                now, now));
        when(stock.reserveStock(eq(productId), eq(variantId), eq(2), any(), eq(null)))
                .thenReturn(new ReservationView(UUID.randomUUID(), productId, variantId, 2, "ref",
                        ReservationStatus.ACTIVE, now, null));
        when(store.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderView order = service.createOrder(new CreateOrder(
                customerId, List.of(new CreateOrder.Line(productId, variantId, 2))));

        OrderView.LineView line = order.lines().getFirst();
        assertThat(line.productName()).isEqualTo("Camiseta Rafaga");
        assertThat(line.sku()).isEqualTo("CAM-01-M");
        assertThat(line.variantName()).isEqualTo("Talla M");
        assertThat(line.lineTotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void aggregatesOrderReportByStatusExcludingCancelledFromGrossAmount() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Order pending = new Order(TENANT, UUID.randomUUID(), from.plusSeconds(60));
        pending.addLine(UUID.randomUUID(), null, 1, new BigDecimal("40.00"), "A", "SKU-A", null);

        Order confirmed = new Order(TENANT, UUID.randomUUID(), from.plusSeconds(120));
        confirmed.addLine(UUID.randomUUID(), null, 2, new BigDecimal("30.00"), "B", "SKU-B", null);
        confirmed.confirm(from.plusSeconds(180));

        Order cancelled = new Order(TENANT, UUID.randomUUID(), from.plusSeconds(240));
        cancelled.addLine(UUID.randomUUID(), null, 1, new BigDecimal("100.00"), "C", "SKU-C", null);
        cancelled.cancel(from.plusSeconds(300));

        when(store.findAll(eq(TENANT), eq(new OrderSearchFilter(null, null, from, to)), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(pending, confirmed, cancelled)));

        OrderReportView report = service.getOrderReport(from, to);

        assertThat(report.totalOrders()).isEqualTo(3);
        assertThat(report.grossOrderAmount()).isEqualByComparingTo("100.00");
        assertThat(report.averageOrderValue()).isEqualByComparingTo("50.00");
        assertThat(report.paymentDisclaimer()).isEqualTo(OrderReportView.PAYMENT_DISCLAIMER);
        assertThat(report.byStatus())
                .anySatisfy(b -> {
                    assertThat(b.status()).isEqualTo("CONFIRMED");
                    assertThat(b.orderCount()).isEqualTo(1);
                    assertThat(b.totalAmount()).isEqualByComparingTo("60.00");
                })
                .anySatisfy(b -> {
                    assertThat(b.status()).isEqualTo("CANCELLED");
                    assertThat(b.orderCount()).isEqualTo(1);
                    assertThat(b.totalAmount()).isEqualByComparingTo("100.00");
                });
    }
}
