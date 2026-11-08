package com.rafaga.backend.catalog.domain.event;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ProductUpdatedEvent(UUID productId, String tenantId, Map<String, Object> changes) {
    public ProductUpdatedEvent {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        changes = Map.copyOf(Objects.requireNonNull(changes, "changes must not be null"));
    }
}
