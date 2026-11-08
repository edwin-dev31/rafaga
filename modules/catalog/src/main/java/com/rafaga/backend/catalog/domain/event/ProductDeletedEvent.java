package com.rafaga.backend.catalog.domain.event;

import java.util.Objects;
import java.util.UUID;

public record ProductDeletedEvent(UUID productId, String tenantId) {
    public ProductDeletedEvent {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
    }
}
