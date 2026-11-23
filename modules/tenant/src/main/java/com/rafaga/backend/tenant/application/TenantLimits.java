package com.rafaga.backend.tenant.application;

import java.time.Instant;
import java.util.Objects;

public record TenantLimits(
        String tenantId,
        String planId,
        int maxProducts,
        long maxAssets,
        long maxStorageBytes,
        int maxAdminUsers,
        int maxDomains,
        Instant updatedAt) {
    public TenantLimits {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        if (maxProducts < 0) {
            throw new IllegalArgumentException("maxProducts must not be negative");
        }
        if (maxAssets < 0) {
            throw new IllegalArgumentException("maxAssets must not be negative");
        }
        if (maxStorageBytes < 0) {
            throw new IllegalArgumentException("maxStorageBytes must not be negative");
        }
        if (maxAdminUsers < 0) {
            throw new IllegalArgumentException("maxAdminUsers must not be negative");
        }
        if (maxDomains < 0) {
            throw new IllegalArgumentException("maxDomains must not be negative");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
