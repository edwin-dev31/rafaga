package com.rafaga.backend.inventory.infrastructure.events;

import com.rafaga.backend.catalog.domain.event.ProductCreatedEvent;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.domain.event.OrderCancelledEvent;
import com.rafaga.backend.common.domain.event.OrderConfirmedEvent;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
public class InventoryEventHandlers {
    private final StockService stock;
    private final TransactionOperations transactions;

    public InventoryEventHandlers(StockService stock,
            @Qualifier("inventoryEventTransactions") TransactionOperations transactions) {
        this.stock = stock;
        this.transactions = transactions;
    }

    @ApplicationModuleListener
    public void onProductCreated(ProductCreatedEvent event) {
        withTenant(event.tenantId(), () -> {
            try {
                transactions.executeWithoutResult(status -> stock.initializeProduct(event.productId()));
            } catch (CatalogNotFoundException ignored) {
                // Product creation can be followed by deletion before this asynchronous listener runs.
            }
            return null;
        });
    }

    @ApplicationModuleListener
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        withTenant(event.tenantId(), () -> {
            transactions.executeWithoutResult(status -> {
                for (OrderConfirmedEvent.Line line : event.lines()) {
                    stock.reserveStock(line.productId(), line.variantId(), line.quantity(),
                            event.orderId().toString(), null);
                }
            });
            return null;
        });
    }

    @ApplicationModuleListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        withTenant(event.tenantId(), () -> {
            transactions.executeWithoutResult(status -> {
                for (ReservationView reservation : stock.getActiveReservations(event.orderId().toString())) {
                    stock.releaseReservation(reservation.id());
                }
            });
            return null;
        });
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        TenantContext.setCurrentTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
