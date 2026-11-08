package com.rafaga.backend.catalog.domain.event;

import java.util.Objects;
import java.util.UUID;

public record ProductCreatedEvent(UUID productId, String tenantId, String sku) {
    public ProductCreatedEvent {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(sku, "sku must not be null");
    }
}
