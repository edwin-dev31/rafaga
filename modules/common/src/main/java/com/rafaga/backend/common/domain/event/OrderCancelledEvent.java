package com.rafaga.backend.common.domain.event;

import java.util.Objects;
import java.util.UUID;

public record OrderCancelledEvent(UUID orderId, String tenantId) {
    public OrderCancelledEvent {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(tenantId);
    }
}
