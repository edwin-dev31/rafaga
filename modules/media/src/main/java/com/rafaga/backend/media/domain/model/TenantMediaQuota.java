package com.rafaga.backend.media.domain.model;

public record TenantMediaQuota(
    String tenantId,
    long maxAssets,
    long maxStorageBytes,
    long usedAssets,
    long usedStorageBytes,
    long reservedAssets,
    long reservedBytes
) {
    public long effectiveUsedBytes() {
        return usedStorageBytes + reservedBytes;
    }

    public long effectiveUsedAssets() {
        return usedAssets + reservedAssets;
    }

    public boolean canReserve(long estimatedBytes) {
        return effectiveUsedAssets() < maxAssets
            && effectiveUsedBytes() + estimatedBytes <= maxStorageBytes;
    }

    public boolean isStorageExceeded() {
        return effectiveUsedBytes() > maxStorageBytes;
    }

    public boolean isCountExceeded() {
        return effectiveUsedAssets() >= maxAssets;
    }
}
