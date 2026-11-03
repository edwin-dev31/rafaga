package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.FeatureFlagUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class FeatureFlagServiceTest {
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void readsFeatureFlagsForTheCurrentTenant() {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findById("tenant-1")).thenReturn(Optional.of(tenant(Map.of("analytics", false))));
        FeatureFlagService service = new FeatureFlagService(registry, mock(ApplicationEventPublisher.class));
        TenantContext.setCurrentTenantId("tenant-1");

        assertThat(service.isFeatureEnabled("analytics")).isFalse();
        assertThat(service.getAllFeatures()).containsEntry("analytics", false);
    }

    @Test
    void updatesAFlagAndPublishesEvent() {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findById("tenant-1")).thenReturn(Optional.of(tenant(Map.of("analytics", false))));
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        FeatureFlagService service = new FeatureFlagService(registry, events);

        service.updateFeatureFlag("tenant-1", "analytics", true);

        verify(registry).update(tenant(Map.of("analytics", true)));
        verify(events).publishEvent(new FeatureFlagUpdatedEvent("tenant-1", "analytics", true));
    }

    private TenantConfig tenant(Map<String, Boolean> flags) {
        return new TenantConfig("tenant-1", "Demo Store", List.of("demo.test"), DeploymentMode.SHARED_DB,
                null, "starter", TenantStatus.ACTIVE, flags);
    }
}
