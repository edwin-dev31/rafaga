package com.rafaga.backend.tenant.domain.event;

public record FeatureFlagUpdatedEvent(String tenantId, String featureName, boolean enabled) {
}
