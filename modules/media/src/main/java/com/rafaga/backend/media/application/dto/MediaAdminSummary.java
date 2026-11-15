package com.rafaga.backend.media.application.dto;

import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tenant-scoped administrative view built from the media database under RLS. */
public record MediaAdminSummary(
        TenantMediaQuota quota,
        Map<AssetStatus, Long> assetsByStatus,
        List<FailedAsset> recentFailures,
        List<PendingCleanupAsset> pendingCleanupAssets) {

    public record FailedAsset(UUID id, String failureCode, String failureReason, Instant failedAt) {
    }

    public record PendingCleanupAsset(UUID id, Instant deleteRequestedAt) {
    }
}
