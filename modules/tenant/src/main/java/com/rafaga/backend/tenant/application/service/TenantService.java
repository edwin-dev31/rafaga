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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private final TenantRegistry tenantRegistry;
    private final PlanFeatureDefaults planFeatureDefaults;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantLimitsService limitsService;

    public TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher) {
        this(tenantRegistry, planFeatureDefaults, eventPublisher, (TenantLimitsService) null);
    }

    @Autowired
    public TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<TenantLimitsService> limitsServiceProvider) {
        this(tenantRegistry, planFeatureDefaults, eventPublisher, limitsServiceProvider.getIfAvailable());
    }

    TenantService(
            TenantRegistry tenantRegistry,
            PlanFeatureDefaults planFeatureDefaults,
            ApplicationEventPublisher eventPublisher,
            TenantLimitsService limitsService) {
        this.tenantRegistry = tenantRegistry;
        this.planFeatureDefaults = planFeatureDefaults;
        this.eventPublisher = eventPublisher;
        this.limitsService = limitsService;
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
        TenantConfig updated = new TenantConfig(
                current.tenantId(),
                update.name(),
                update.domains(),
                update.deploymentMode(),
                databaseConfig(update.databaseConfigId()),
                update.planId(),
                current.status(),
                current.featureFlags());
        tenantRegistry.update(updated);
        if (planChanged && limitsService != null) {
            limitsService.syncWithPlan(tenantId, update.planId());
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
        eventPublisher.publishEvent(new TenantStatusChangedEvent(tenantId, current.status(), TenantStatus.INACTIVE));
        return inactive;
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
