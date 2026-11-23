package com.rafaga.backend.tenant.domain.event;

import java.time.Instant;

public record TenantLimitsUpdatedEvent(
        String tenantId,
        String planId,
        int maxProducts,
        long maxAssets,
        long maxStorageBytes,
        int maxAdminUsers,
        int maxDomains,
        Instant occurredAt) {
}
