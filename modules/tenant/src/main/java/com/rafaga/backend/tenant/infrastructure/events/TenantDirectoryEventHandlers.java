package com.rafaga.backend.tenant.infrastructure.events;

import com.rafaga.backend.tenant.application.service.TenantDirectoryService;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.event.TenantStatusChangedEvent;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.cache.TenantConfigCache;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Persisted Modulith event delivery invalidates derived tenant cache and syncs tenant deactivation. */
@Component
class TenantDirectoryEventHandlers {
    private final TenantConfigCache cache;
    private final TenantDirectoryService directoryService;

    TenantDirectoryEventHandlers(TenantConfigCache cache, TenantDirectoryService directoryService) {
        this.cache = cache;
        this.directoryService = directoryService;
    }

    @ApplicationModuleListener
    void onDirectoryChanged(TenantDirectoryChangedEvent event) {
        cache.invalidate("directory:" + event.hostname());
    }

    @ApplicationModuleListener
    void onTenantStatusChanged(TenantStatusChangedEvent event) {
        if (event.newStatus() == TenantStatus.INACTIVE) {
            directoryService.suspendAllDomainsForTenant(event.tenantId());
        }
    }
}
