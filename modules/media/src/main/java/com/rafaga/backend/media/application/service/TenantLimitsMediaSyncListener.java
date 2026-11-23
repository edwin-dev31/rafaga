package com.rafaga.backend.media.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.port.out.MediaRepository;
import com.rafaga.backend.tenant.domain.event.TenantLimitsUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Keeps tenant_media_quotas aligned with tenant plan limits when planId or overrides change. */
@Component
public class TenantLimitsMediaSyncListener {
    private static final Logger log = LoggerFactory.getLogger(TenantLimitsMediaSyncListener.class);
    private final MediaRepository mediaRepository;

    public TenantLimitsMediaSyncListener(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @EventListener
    public void onTenantLimitsUpdated(TenantLimitsUpdatedEvent event) {
        String previousTenantId = TenantContext.isSet() ? TenantContext.getCurrentTenantId() : null;
        TenantContext.setCurrentTenantId(event.tenantId());
        try {
            mediaRepository.updateQuotaLimits(event.tenantId(), event.maxAssets(), event.maxStorageBytes());
            log.info("event=media.quota_synced tenantId={} planId={} maxAssets={} maxStorageBytes={}",
                    event.tenantId(), event.planId(), event.maxAssets(), event.maxStorageBytes());
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(previousTenantId);
            }
        }
    }
}
