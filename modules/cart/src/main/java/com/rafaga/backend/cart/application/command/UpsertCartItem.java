package com.rafaga.backend.cart.application.command;

import java.util.Objects;
import java.util.UUID;

public record UpsertCartItem(UUID customerId, UUID productId, UUID variantId, int quantity) {
    public UpsertCartItem {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }
}
