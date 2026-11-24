package com.rafaga.backend.orders.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.cart.application.exception.CartConflictException;
import com.rafaga.backend.cart.domain.event.CartCheckedOutEvent;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import com.rafaga.backend.inventory.domain.model.ReservationStatus;
import com.rafaga.backend.orders.application.command.CreateOrder;
import com.rafaga.backend.orders.application.port.OrderStore;
import com.rafaga.backend.orders.application.service.OrderService;
import com.rafaga.backend.orders.application.service.OrderView;
import com.rafaga.backend.orders.domain.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OrderCheckoutIdempotencyTest {

    private static final String TENANT = "tenant-a";

    private OrderStore store;
    private ProductService products;
    private StockService stock;
    private ApplicationEventPublisher events;
    private OrderService service;
    private CartCheckoutEventHandler handler;

    @BeforeEach
    void setUp() {
        store = mock(OrderStore.class);
        products = mock(ProductService.class);
        stock = mock(StockService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new OrderService(store, products, stock, events);
        handler = new CartCheckoutEventHandler(service);
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsExistingOrderIdempotentlyWhenCartWasAlreadyCheckedOut() {
        UUID cartId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order existing = Order.fromCart(TENANT, cartId, customerId, Instant.now());
        existing.addLine(productId, null, 2, new BigDecimal("15.00"));
        when(store.findByCartId(TENANT, cartId)).thenReturn(Optional.of(existing));

        OrderView result = service.createOrderFromCart(
                cartId, customerId, List.of(new CreateOrder.Line(productId, null, 2)));

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.cartId()).isEqualTo(cartId);
        verify(store, never()).save(any());
        verify(stock, never()).reserveStock(any(), any(), eq(2), any(), any());
    }

    @Test
    void allowsGuestCheckoutOrderWithoutCustomerId() {
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(store.findByCartId(TENANT, cartId)).thenReturn(Optional.empty());
        when(products.getProductById(productId)).thenReturn(new ProductView(
                productId, "SKU-1", "Guest Item", "desc",
                new BigDecimal("25.00"), ProductStatus.ACTIVE, Set.of(), List.of(), null, null));
        when(stock.reserveStock(eq(productId), eq(null), eq(1), any(), eq(null)))
                .thenReturn(new ReservationView(UUID.randomUUID(), productId, null, 1, "ref",
                        ReservationStatus.ACTIVE, Instant.now(), null));
        when(store.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderView result = service.createOrderFromCart(
                cartId, null, List.of(new CreateOrder.Line(productId, null, 1)));

        assertThat(result.cartId()).isEqualTo(cartId);
        assertThat(result.customerId()).isNull();
        assertThat(result.publicToken()).isNotBlank().hasSizeGreaterThanOrEqualTo(32);
        assertThat(result.total()).isEqualByComparingTo("25.00");
    }

    @Test
    void translatesInsufficientStockIntoCartConflictException() {
        UUID cartId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(store.findByCartId(TENANT, cartId)).thenReturn(Optional.empty());
        when(products.getProductById(productId)).thenReturn(new ProductView(
                productId, "SKU-2", "Scarce Item", "desc",
                new BigDecimal("10.00"), ProductStatus.ACTIVE, Set.of(), List.of(), null, null));
        when(stock.reserveStock(eq(productId), eq(null), eq(5), any(), eq(null)))
                .thenThrow(new InsufficientStockException("Insufficient available stock"));

        CartCheckedOutEvent event = new CartCheckedOutEvent(
                cartId, TENANT, customerId,
                List.of(new CartCheckedOutEvent.Line(productId, null, 5, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> handler.onCartCheckedOut(event))
                .isInstanceOf(CartConflictException.class)
                .hasMessageContaining("Insufficient");
    }
}
