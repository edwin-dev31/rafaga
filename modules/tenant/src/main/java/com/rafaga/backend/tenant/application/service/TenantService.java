package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.command.TenantRegistration;
import com.rafaga.backend.tenant.application.command.TenantUpdate;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.plan.PlanFeatureDefaults;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantCreatedEvent;
import com.rafaga.backend.tenant.domain.event.TenantStatusChangedEvent;
import com.rafaga.backend.tenant.domain.event.TenantUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private final TenantRegistry tenantRegistry;
    private final PlanFeatureDefaults planFeatureDefaults;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantLimitsService limitsService;
    private final TenantDirectoryService directoryService;
    private final FeatureFlagService featureFlagService;

    public TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher) {
        this(tenantRegistry, planFeatureDefaults, eventPublisher, (TenantLimitsService) null, null, null);
    }

    @Autowired
    public TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<TenantLimitsService> limitsServiceProvider,
            ObjectProvider<TenantDirectoryService> directoryServiceProvider,
            ObjectProvider<FeatureFlagService> featureFlagServiceProvider) {
        this(
                tenantRegistry,
                planFeatureDefaults,
                eventPublisher,
                limitsServiceProvider.getIfAvailable(),
                directoryServiceProvider.getIfAvailable(),
                featureFlagServiceProvider.getIfAvailable());
    }

    TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher,
            TenantLimitsService limitsService) {
        this(tenantRegistry, planFeatureDefaults, eventPublisher, limitsService, null, null);
    }

    TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher,
            TenantLimitsService limitsService,
            TenantDirectoryService directoryService) {
        this(tenantRegistry, planFeatureDefaults, eventPublisher, limitsService, directoryService, null);
    }

    TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher,
            TenantLimitsService limitsService,
            TenantDirectoryService directoryService,
            FeatureFlagService featureFlagService) {
        this.tenantRegistry = tenantRegistry;
        this.planFeatureDefaults = planFeatureDefaults;
        this.eventPublisher = eventPublisher;
        this.limitsService = limitsService;
        this.directoryService = directoryService;
        this.featureFlagService = featureFlagService;
    }

    @Transactional
    public TenantConfig registerTenant(TenantRegistration registration) {
        String tenantId = UUID.randomUUID().toString();
        TenantLimits planLimits = PlanLimitsDefaults.forPlan(tenantId, registration.planId(), Instant.now());
        if (registration.domains().size() > planLimits.maxDomains()) {
            throw new TenantLimitExceededException("domains", planLimits.maxDomains(), registration.domains().size());
        }
        TenantConfig config = new TenantConfig(
                tenantId,
                registration.name(),
                registration.domains(),
                registration.deploymentMode(),
                databaseConfig(registration.databaseConfigId()),
                registration.planId(),
                TenantStatus.ACTIVE,
                planFeatureDefaults.featuresFor(registration.planId()));
        tenantRegistry.register(config);
        if (limitsService != null) {
            limitsService.syncWithPlan(config.tenantId(), config.planId());
        }
        eventPublisher.publishEvent(new TenantCreatedEvent(config.tenantId(), config.name(), config.deploymentMode()));
        return config;
    }

    @Transactional
    public TenantConfig updateTenant(String tenantId, TenantUpdate update) {
        TenantConfig current = getTenantById(tenantId);
        boolean planChanged = !current.planId().equals(update.planId());
        DatabaseConfig effectiveDatabaseConfig = (update.databaseConfigId() == null || update.databaseConfigId().isBlank())
                ? current.databaseConfig()
                : databaseConfig(update.databaseConfigId());
        if (update.deploymentMode() == com.rafaga.backend.tenant.domain.model.DeploymentMode.DEDICATED_DB
                && effectiveDatabaseConfig == null) {
            throw new IllegalArgumentException("Dedicated database tenants require a databaseConfigId");
        }
        Map<String, Boolean> effectiveFeatureFlags = current.featureFlags();
        if (planChanged) {
            Map<String, Boolean> planDefaults = planFeatureDefaults.featuresFor(update.planId());
            if (planDefaults != null && !planDefaults.isEmpty()) {
                effectiveFeatureFlags = planDefaults;
            }
        }
        TenantConfig updated = new TenantConfig(
                current.tenantId(),
                update.name(),
                update.domains(),
                update.deploymentMode(),
                effectiveDatabaseConfig,
                update.planId(),
                current.status(),
                effectiveFeatureFlags);
        tenantRegistry.update(updated);
        if (planChanged) {
            if (limitsService != null) {
                limitsService.syncWithPlan(tenantId, update.planId());
            }
            if (featureFlagService != null) {
                java.util.Set<String> affectedFeatures = new java.util.LinkedHashSet<>(current.featureFlags().keySet());
                affectedFeatures.addAll(effectiveFeatureFlags.keySet());
                featureFlagService.evictTenantFeatureCache(tenantId, affectedFeatures);
            }
        }
        eventPublisher.publishEvent(new TenantUpdatedEvent(tenantId, changedFields(current, updated)));
        return updated;
    }

    @Transactional(readOnly = true)
    public TenantConfig getTenantById(String tenantId) {
        return tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Transactional(readOnly = true)
    public List<TenantConfig> getAllTenants() {
        return tenantRegistry.findAll();
    }

    @Transactional
    public TenantConfig deactivateTenant(String tenantId) {
        TenantConfig current = getTenantById(tenantId);
        if (current.status() == TenantStatus.INACTIVE) {
            return current;
        }
        TenantConfig inactive = new TenantConfig(
                current.tenantId(),
                current.name(),
                current.domains(),
                current.deploymentMode(),
                current.databaseConfig(),
                current.planId(),
                TenantStatus.INACTIVE,
                current.featureFlags());
        tenantRegistry.update(inactive);
        if (directoryService != null) {
            directoryService.suspendAllDomainsForTenant(tenantId);
        }
        eventPublisher.publishEvent(new TenantStatusChangedEvent(tenantId, current.status(), TenantStatus.INACTIVE));
        log.info("event=tenant.deactivate outcome=SUCCESS tenantId={} actor={}", tenantId, currentActor());
        return inactive;
    }

    @Transactional
    public TenantConfig reactivateTenant(String tenantId) {
        TenantConfig current = getTenantById(tenantId);
        if (current.status() == TenantStatus.ACTIVE) {
            return current;
        }
        TenantConfig active = new TenantConfig(
                current.tenantId(),
                current.name(),
                current.domains(),
                current.deploymentMode(),
                current.databaseConfig(),
                current.planId(),
                TenantStatus.ACTIVE,
                current.featureFlags());
        tenantRegistry.update(active);
        if (directoryService != null) {
            directoryService.reactivateSuspendedDomainsForTenant(tenantId);
        }
        eventPublisher.publishEvent(new TenantStatusChangedEvent(tenantId, current.status(), TenantStatus.ACTIVE));
        log.info("event=tenant.reactivate outcome=SUCCESS tenantId={} actor={}", tenantId, currentActor());
        return active;
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }

    private DatabaseConfig databaseConfig(String databaseConfigId) {
        if (databaseConfigId == null || databaseConfigId.isBlank()) {
            return null;
        }
        return new DatabaseConfig(databaseConfigId);
    }

    private Map<String, Object> changedFields(TenantConfig before, TenantConfig after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!before.name().equals(after.name())) {
            changes.put("name", after.name());
        }
        if (!before.domains().equals(after.domains())) {
            changes.put("domains", after.domains());
        }
        if (before.deploymentMode() != after.deploymentMode()) {
            changes.put("deploymentMode", after.deploymentMode());
        }
        if (!java.util.Objects.equals(before.databaseConfig(), after.databaseConfig())) {
            changes.put("databaseConfigId", after.databaseConfig());
        }
        if (!before.planId().equals(after.planId())) {
            changes.put("planId", after.planId());
        }
        return changes;
    }
}
