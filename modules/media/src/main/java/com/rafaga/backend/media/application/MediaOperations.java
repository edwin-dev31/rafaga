package com.rafaga.backend.media.application;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import org.springframework.stereotype.Service;

/** Cross-module, super-admin media operations with explicit tenant scoping. */
@Service
public class MediaOperations {
    private final MediaService mediaService;

    public MediaOperations(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    public TenantQuota quotaFor(String tenantId) {
        String previous = TenantContext.isSet() ? TenantContext.getCurrentTenantId() : null;
        TenantContext.setCurrentTenantId(tenantId);
        try {
            TenantMediaQuota quota = mediaService.getQuotaStats();
            return new TenantQuota(quota.maxAssets(), quota.maxStorageBytes(), quota.usedAssets(),
                    quota.usedStorageBytes(), quota.reservedAssets(), quota.reservedBytes());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(previous);
            }
        }
    }

    public record TenantQuota(long maxAssets, long maxStorageBytes, long usedAssets,
            long usedStorageBytes, long reservedAssets, long reservedBytes) { }
}
