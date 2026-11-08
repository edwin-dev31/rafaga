package com.rafaga.backend.inventory.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.domain.event.ProductCreatedEvent;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.domain.event.OrderCancelledEvent;
import com.rafaga.backend.common.domain.event.OrderConfirmedEvent;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryEventHandlersTest {
    @Test
    void propagatesAndClearsTenantContextForProductAndOrderEvents() {
        StockService stock = mock(StockService.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        InventoryEventHandlers handlers = new InventoryEventHandlers(stock, new TransactionTemplate(manager));
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        doAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");
            return null;
        }).when(stock).initializeProduct(productId);
        when(stock.reserveStock(eq(productId), eq(null), eq(2), anyString(), eq(null)))
                .thenAnswer(invocation -> {
                    assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");
                    return null;
                });
        when(stock.getActiveReservations(orderId.toString())).thenAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");
            return List.of(new ReservationView(reservationId, productId, null, 2,
                    orderId.toString(), null, null, null));
        });
        when(stock.releaseReservation(reservationId)).thenAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");
            return null;
        });

        handlers.onProductCreated(new ProductCreatedEvent(productId, "tenant-a", "SKU-1"));
        assertThat(TenantContext.isSet()).isFalse();
        handlers.onOrderConfirmed(new OrderConfirmedEvent(orderId, "tenant-a",
                List.of(new OrderConfirmedEvent.Line(productId, null, 2))));
        assertThat(TenantContext.isSet()).isFalse();
        handlers.onOrderCancelled(new OrderCancelledEvent(orderId, "tenant-a"));
        assertThat(TenantContext.isSet()).isFalse();
        verify(stock).releaseReservation(reservationId);
    }
}
