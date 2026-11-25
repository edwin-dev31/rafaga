package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.plan.PlanFeatureDefaults;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.FeatureFlagUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);
    private static final String FEATURE_FLAGS_CACHE = "feature-flags";

    private final TenantRegistry tenantRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final PlanFeatureDefaults planFeatureDefaults;
    private final CacheManager cacheManager;

    public FeatureFlagService(TenantRegistry tenantRegistry, ApplicationEventPublisher eventPublisher) {
        this(tenantRegistry, eventPublisher, (PlanFeatureDefaults) null, null);
    }

    @Autowired
    public FeatureFlagService(
            TenantRegistry tenantRegistry,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<PlanFeatureDefaults> planFeatureDefaultsProvider,
            ObjectProvider<CacheManager> cacheManagerProvider) {
        this(
                tenantRegistry,
                eventPublisher,
                planFeatureDefaultsProvider.getIfAvailable(),
                cacheManagerProvider.getIfAvailable());
    }

    FeatureFlagService(
            TenantRegistry tenantRegistry,
            ApplicationEventPublisher eventPublisher,
            PlanFeatureDefaults planFeatureDefaults,
            CacheManager cacheManager) {
        this.tenantRegistry = tenantRegistry;
        this.eventPublisher = eventPublisher;
        this.planFeatureDefaults = planFeatureDefaults;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = FEATURE_FLAGS_CACHE,
            key = "T(com.rafaga.backend.common.application.tenant.TenantContext).getCurrentTenantId() + ':' + #p0")
    public boolean isFeatureEnabled(String featureName) {
        boolean enabled = getAllFeatures().getOrDefault(featureName, false);
        if (!enabled) {
            log.warn("Feature access denied for tenant {} and feature {}", TenantContext.getCurrentTenantId(), featureName);
        }
        return enabled;
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getAllFeatures() {
        String tenantId = TenantContext.getCurrentTenantId();
        return findTenant(tenantId).featureFlags();
    }

    @Transactional(readOnly = true)
    public TenantConfig getTenantFeatures(String tenantId) {
        return findTenant(tenantId);
    }

    @Transactional
    @CacheEvict(cacheNames = FEATURE_FLAGS_CACHE, key = "#p0 + ':' + #p1")
    public TenantConfig updateFeatureFlag(String tenantId, String featureName, boolean enabled) {
        if (featureName == null || featureName.isBlank()) {
            throw new IllegalArgumentException("featureName must not be blank");
        }
        String normalizedFeature = featureName.trim();
        TenantConfig tenant = findTenant(tenantId);
        TenantConfig updated = withFeatureFlag(tenant, normalizedFeature, enabled);
        tenantRegistry.update(updated);
        evictTenantFeatureCache(tenantId, Set.of(normalizedFeature));
        eventPublisher.publishEvent(new FeatureFlagUpdatedEvent(tenantId, normalizedFeature, enabled));
        return updated;
    }

    @Transactional
    public TenantConfig syncWithCurrentPlan(String tenantId) {
        TenantConfig current = findTenant(tenantId);
        if (planFeatureDefaults == null) {
            return current;
        }
        Map<String, Boolean> defaults = planFeatureDefaults.featuresFor(current.planId());
        if (defaults == null || defaults.isEmpty()) {
            return current;
        }
        TenantConfig updated = new TenantConfig(
                current.tenantId(),
                current.name(),
                current.domains(),
                current.deploymentMode(),
                current.databaseConfig(),
                current.planId(),
                current.status(),
                defaults);
        tenantRegistry.update(updated);
        Set<String> affectedKeys = new LinkedHashSet<>(current.featureFlags().keySet());
        affectedKeys.addAll(defaults.keySet());
        evictTenantFeatureCache(tenantId, affectedKeys);
        for (String key : affectedKeys) {
            boolean before = current.featureFlags().getOrDefault(key, false);
            boolean after = defaults.getOrDefault(key, false);
            if (!Objects.equals(before, after)) {
                eventPublisher.publishEvent(new FeatureFlagUpdatedEvent(tenantId, key, after));
            }
        }
        return updated;
    }

    public void evictTenantFeatureCache(String tenantId, Collection<String> featureNames) {
        if (cacheManager == null || featureNames == null || featureNames.isEmpty()) {
            return;
        }
        Cache cache = cacheManager.getCache(FEATURE_FLAGS_CACHE);
        if (cache == null) {
            return;
        }
        for (String featureName : featureNames) {
            cache.evict(tenantId + ":" + featureName);
        }
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
