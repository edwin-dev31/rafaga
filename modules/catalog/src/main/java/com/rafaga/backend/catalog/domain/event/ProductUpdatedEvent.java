package com.rafaga.backend.catalog.domain.event;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

public record ProductUpdatedEvent(UUID productId, String tenantId, Map<String, Object> changes) {
    public ProductUpdatedEvent {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        changes = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(changes, "changes must not be null")));
    }
}
