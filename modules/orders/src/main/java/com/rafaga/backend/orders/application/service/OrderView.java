package com.rafaga.backend.orders.application.service;

import com.rafaga.backend.orders.domain.model.Order;
import com.rafaga.backend.orders.domain.model.OrderLine;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, UUID customerId, UUID cartId, String publicToken, OrderStatus status,
        BigDecimal subtotal, BigDecimal total, List<LineView> lines, Instant createdAt,
        Instant updatedAt, Instant confirmedAt, Instant completedAt) {
    public OrderView(UUID id, UUID customerId, OrderStatus status, BigDecimal subtotal,
            BigDecimal total, List<LineView> lines, Instant createdAt, Instant updatedAt,
            Instant confirmedAt, Instant completedAt) {
        this(id, customerId, null, null, status, subtotal, total, lines, createdAt, updatedAt, confirmedAt, completedAt);
    }

    public String statusName() {
        return status.name();
    }

    public static OrderView from(Order order) {
        return new OrderView(order.getId(), order.getCustomerId(), order.getCartId(), order.getPublicToken(),
                order.getStatus(), order.getSubtotal(), order.getTotal(), order.getLines().stream()
                        .map(LineView::from).toList(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getConfirmedAt(), order.getCompletedAt());
    }

    public record LineView(UUID id, UUID productId, UUID variantId, int quantity,
            BigDecimal unitPrice, BigDecimal lineTotal) {
        static LineView from(OrderLine line) {
            return new LineView(line.getId(), line.getProductId(), line.getVariantId(),
                    line.getQuantity(), line.getUnitPrice(), line.getLineTotal());
        }
    }
}
