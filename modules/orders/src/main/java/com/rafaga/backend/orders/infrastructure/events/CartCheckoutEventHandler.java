package com.rafaga.backend.orders.infrastructure.events;

import com.rafaga.backend.cart.application.exception.CartConflictException;
import com.rafaga.backend.cart.domain.event.CartCheckedOutEvent;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import com.rafaga.backend.orders.application.command.CreateOrder;
import com.rafaga.backend.orders.application.exception.OrderConflictException;
import com.rafaga.backend.orders.application.service.OrderService;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Synchronously converts a checked-out cart into a tenant-scoped order with reserved stock.
 * Runs within the checkout transaction so stock failures roll back the cart transition atomically,
 * while {@link OrderService#createOrderFromCart} remains idempotent across retries.
 */
@Component
class CartCheckoutEventHandler {
    private final OrderService orders;

    CartCheckoutEventHandler(OrderService orders) {
        this.orders = orders;
    }

    @EventListener
    void onCartCheckedOut(CartCheckedOutEvent event) {
        String previousTenant = TenantContext.getCurrentTenantId();
        boolean switched = event.tenantId() != null && !event.tenantId().equals(previousTenant);
        if (switched) {
            TenantContext.setCurrentTenantId(event.tenantId());
        }
        try {
            List<CreateOrder.Line> lines = event.items().stream()
                    .map(item -> new CreateOrder.Line(item.productId(), item.variantId(), item.quantity()))
                    .toList();
            orders.createOrderFromCart(event.cartId(), event.customerId(), lines);
        } catch (InsufficientStockException | StockNotFoundException | OrderConflictException ex) {
            throw new CartConflictException(ex.getMessage());
        } finally {
            if (switched) {
                if (previousTenant == null) {
                    TenantContext.clear();
                } else {
                    TenantContext.setCurrentTenantId(previousTenant);
                }
            }
        }
    }
}
