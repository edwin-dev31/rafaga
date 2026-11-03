package com.rafaga.backend.tenant.domain.event;

import java.util.Map;

public record TenantUpdatedEvent(String tenantId, Map<String, Object> changes) {
    public TenantUpdatedEvent {
        changes = Map.copyOf(changes);
    }
}
