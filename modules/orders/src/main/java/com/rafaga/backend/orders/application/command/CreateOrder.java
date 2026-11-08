package com.rafaga.backend.orders.application.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateOrder(UUID customerId, List<Line> lines) {
    public CreateOrder {
        Objects.requireNonNull(customerId, "customerId must not be null");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines must not be null"));
        if (lines.isEmpty()) throw new IllegalArgumentException("Order must have at least one line");
    }

    public record Line(UUID productId, UUID variantId, int quantity) {
        public Line {
            Objects.requireNonNull(productId, "productId must not be null");
            if (quantity <= 0) throw new IllegalArgumentException("Line quantity must be positive");
        }
    }
}
