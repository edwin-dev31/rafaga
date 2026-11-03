package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.FeatureFlagUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private final TenantRegistry tenantRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public FeatureFlagService(TenantRegistry tenantRegistry, ApplicationEventPublisher eventPublisher) {
        this.tenantRegistry = tenantRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String featureName) {
        return getAllFeatures().getOrDefault(featureName, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getAllFeatures() {
        String tenantId = TenantContext.getCurrentTenantId();
        return tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId))
                .featureFlags();
    }

    @Transactional
    public void updateFeatureFlag(String tenantId, String featureName, boolean enabled) {
        TenantConfig tenant = findTenant(tenantId);
        tenantRegistry.update(withFeatureFlag(tenant, featureName, enabled));
        eventPublisher.publishEvent(new FeatureFlagUpdatedEvent(tenantId, featureName, enabled));
    }

    private TenantConfig findTenant(String tenantId) {
        return tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    private TenantConfig withFeatureFlag(TenantConfig tenant, String featureName, boolean enabled) {
        Map<String, Boolean> featureFlags = new LinkedHashMap<>(tenant.featureFlags());
        featureFlags.put(featureName, enabled);
        return new TenantConfig(
                tenant.tenantId(),
                tenant.name(),
                tenant.domains(),
                tenant.deploymentMode(),
                tenant.databaseConfig(),
                tenant.planId(),
                tenant.status(),
                featureFlags);
    }
}
