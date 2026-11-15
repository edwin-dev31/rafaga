package com.rafaga.backend.media.infrastructure.web;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.dto.MediaAdminSummary;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only platform administration. Every database read is executed under RLS. */
@RestController
@RequestMapping("/api/admin")
public class MediaAdminController {

    private final TenantRegistry tenantRegistry;
    private final MediaService mediaService;

    public MediaAdminController(TenantRegistry tenantRegistry, MediaService mediaService) {
        this.tenantRegistry = tenantRegistry;
        this.mediaService = mediaService;
    }

    @GetMapping("/media/overview")
    public OverviewResponse overview(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        List<TenantConfig> tenants = tenantRegistry.findAll().stream()
            .sorted(Comparator.comparing(TenantConfig::tenantId))
            .toList();
        long offset = (long) page * size;
        int from = offset >= tenants.size() ? tenants.size() : (int) offset;
        int to = Math.min(from + size, tenants.size());
        List<TenantSummaryResponse> content = tenants.subList(from, to).stream()
            .map(tenant -> inTenant(tenant.tenantId(), () ->
                TenantSummaryResponse.from(tenant, mediaService.getAdminSummary())))
            .toList();
        return new OverviewResponse(content, page, size, tenants.size(), (int) Math.ceil((double) tenants.size() / size));
    }

    @GetMapping("/tenants/{tenantId}/media/summary")
    public TenantSummaryResponse tenantSummary(@PathVariable("tenantId") String tenantId) {
        TenantConfig tenant = tenantRegistry.findById(tenantId)
            .orElseThrow(() -> tenantNotFound(tenantId));
        return inTenant(tenantId, () -> TenantSummaryResponse.from(tenant, mediaService.getAdminSummary()));
    }

    private <T> T inTenant(String tenantId, Supplier<T> action) {
        String previousTenantId = TenantContext.isSet() ? TenantContext.getCurrentTenantId() : null;
        TenantContext.setCurrentTenantId(tenantId);
        try {
            return action.get();
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(previousTenantId);
            }
        }
    }

    public record OverviewResponse(
            List<TenantSummaryResponse> content, int page, int size, long totalElements, int totalPages) {
    }

    public record TenantSummaryResponse(
            String tenantId,
            String tenantName,
            Map<AssetStatus, Long> assetsByStatus,
            QuotaResponse quota,
            List<FailureResponse> recentFailures,
            List<PendingCleanupResponse> pendingCleanupAssets) {
        static TenantSummaryResponse from(TenantConfig tenant, MediaAdminSummary summary) {
            return new TenantSummaryResponse(
                tenant.tenantId(), tenant.name(), summary.assetsByStatus(), QuotaResponse.from(summary.quota()),
                summary.recentFailures().stream().map(FailureResponse::from).toList(),
                summary.pendingCleanupAssets().stream().map(PendingCleanupResponse::from).toList());
        }
    }

    public record QuotaResponse(
            long maxAssets, long maxStorageBytes, long usedAssets, long usedStorageBytes,
            long reservedAssets, long reservedBytes, long effectiveUsedAssets, long effectiveUsedBytes,
            double effectiveAssetUsagePercent, double effectiveStorageUsagePercent) {
        static QuotaResponse from(TenantMediaQuota quota) {
            return new QuotaResponse(quota.maxAssets(), quota.maxStorageBytes(), quota.usedAssets(),
                quota.usedStorageBytes(), quota.reservedAssets(), quota.reservedBytes(),
                quota.effectiveUsedAssets(), quota.effectiveUsedBytes(),
                percentage(quota.effectiveUsedAssets(), quota.maxAssets()),
                percentage(quota.effectiveUsedBytes(), quota.maxStorageBytes()));
        }

        private static double percentage(long value, long limit) {
            return limit == 0 ? 0 : (value * 100.0) / limit;
        }
    }

    public record FailureResponse(String assetId, String code, String reason, Instant failedAt) {
        static FailureResponse from(MediaAdminSummary.FailedAsset asset) {
            return new FailureResponse(asset.id().toString(), asset.failureCode(), asset.failureReason(), asset.failedAt());
        }
    }

    public record PendingCleanupResponse(String assetId, Instant deleteRequestedAt) {
        static PendingCleanupResponse from(MediaAdminSummary.PendingCleanupAsset asset) {
            return new PendingCleanupResponse(asset.id().toString(), asset.deleteRequestedAt());
        }
    }

    private ResponseStatusException tenantNotFound(String tenantId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
    }
}
