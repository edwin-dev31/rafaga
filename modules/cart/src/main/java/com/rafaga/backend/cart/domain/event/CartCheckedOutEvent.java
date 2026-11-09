package com.rafaga.backend.cart.domain.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published when a cart is checked out.
 * Consumers (e.g. Order Management) can use this to create the corresponding order.
 */
public record CartCheckedOutEvent(UUID cartId, String tenantId, UUID customerId, List<Line> items) {

    public record Line(UUID productId, UUID variantId, int quantity, BigDecimal unitPrice) { }
}
