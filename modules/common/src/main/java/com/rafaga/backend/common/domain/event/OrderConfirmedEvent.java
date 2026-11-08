package com.rafaga.backend.common.domain.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Shared order event contract used without a dependency from inventory to orders. */
public record OrderConfirmedEvent(UUID orderId, String tenantId, List<Line> lines) {
    public OrderConfirmedEvent {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(tenantId);
        lines = List.copyOf(Objects.requireNonNull(lines));
    }

    public record Line(UUID productId, UUID variantId, int quantity) {
        public Line {
            Objects.requireNonNull(productId);
            if (quantity <= 0) throw new IllegalArgumentException("Order quantity must be positive");
        }
    }
}
