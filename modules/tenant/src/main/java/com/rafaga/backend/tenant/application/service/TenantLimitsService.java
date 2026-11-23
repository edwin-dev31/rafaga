package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantLimitsUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantPlanLimitsEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantPlanLimitsRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantLimitsService implements TenantLimitsQuery {
    private final TenantRegistry tenantRegistry;
    private final TenantPlanLimitsRepository limitsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public TenantLimitsService(
            TenantRegistry tenantRegistry,
            TenantPlanLimitsRepository limitsRepository,
            ApplicationEventPublisher eventPublisher) {
        this(tenantRegistry, limitsRepository, eventPublisher, Clock.systemUTC());
    }

    TenantLimitsService(
            TenantRegistry tenantRegistry,
            TenantPlanLimitsRepository limitsRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.tenantRegistry = tenantRegistry;
        this.limitsRepository = limitsRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantLimits getLimits(String tenantId) {
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return limitsRepository.findById(tenantId)
                .map(this::toLimits)
                .orElseGet(() -> PlanLimitsDefaults.forPlan(tenantId, tenant.planId(), clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantLimits> findLimits(String tenantId) {
        Optional<TenantPlanLimitsEntity> persisted = limitsRepository.findById(tenantId);
        if (persisted.isPresent()) {
            return Optional.of(toLimits(persisted.get()));
        }
        return tenantRegistry.findById(tenantId)
                .map(tenant -> PlanLimitsDefaults.forPlan(tenantId, tenant.planId(), clock.instant()));
    }

    @Transactional
    public TenantLimits syncWithPlan(String tenantId, String planId) {
        Instant now = clock.instant();
        TenantLimits defaults = PlanLimitsDefaults.forPlan(tenantId, planId, now);
        TenantLimits saved = save(defaults);
        publishUpdated(saved);
        return saved;
    }

    @Transactional
    public TenantLimits syncWithCurrentPlan(String tenantId) {
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return syncWithPlan(tenantId, tenant.planId());
    }

    @Transactional
    public TenantLimits updateLimits(
            String tenantId,
            String requestedPlanId,
            Integer maxProducts,
            Long maxAssets,
            Long maxStorageBytes,
            Integer maxAdminUsers,
            Integer maxDomains) {
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        Instant now = clock.instant();
        boolean planChanged = requestedPlanId != null && !requestedPlanId.isBlank()
                && !requestedPlanId.equals(tenant.planId());
        String effectivePlanId = (requestedPlanId != null && !requestedPlanId.isBlank())
                ? requestedPlanId.trim()
                : tenant.planId();

        TenantLimits base = planChanged
                ? PlanLimitsDefaults.forPlan(tenantId, effectivePlanId, now)
                : limitsRepository.findById(tenantId)
                        .map(this::toLimits)
                        .orElseGet(() -> PlanLimitsDefaults.forPlan(tenantId, effectivePlanId, now));

        TenantLimits updated = new TenantLimits(
                tenantId,
                effectivePlanId,
                maxProducts != null ? maxProducts : base.maxProducts(),
                maxAssets != null ? maxAssets : base.maxAssets(),
                maxStorageBytes != null ? maxStorageBytes : base.maxStorageBytes(),
                maxAdminUsers != null ? maxAdminUsers : base.maxAdminUsers(),
                maxDomains != null ? maxDomains : base.maxDomains(),
                now);

        if (planChanged) {
            tenantRegistry.update(new TenantConfig(
                    tenant.tenantId(),
                    tenant.name(),
                    tenant.domains(),
                    tenant.deploymentMode(),
                    tenant.databaseConfig(),
                    effectivePlanId,
                    tenant.status(),
                    tenant.featureFlags()));
        }

        TenantLimits saved = save(updated);
        publishUpdated(saved);
        return saved;
    }

    private TenantLimits save(TenantLimits limits) {
        TenantPlanLimitsEntity entity = limitsRepository.findById(limits.tenantId())
                .orElseGet(() -> new TenantPlanLimitsEntity(
                        limits.tenantId(),
                        limits.planId(),
                        limits.maxProducts(),
                        limits.maxAssets(),
                        limits.maxStorageBytes(),
                        limits.maxAdminUsers(),
                        limits.maxDomains(),
                        limits.updatedAt()));
        entity.setPlanId(limits.planId());
        entity.setMaxProducts(limits.maxProducts());
        entity.setMaxAssets(limits.maxAssets());
        entity.setMaxStorageBytes(limits.maxStorageBytes());
        entity.setMaxAdminUsers(limits.maxAdminUsers());
        entity.setMaxDomains(limits.maxDomains());
        entity.setUpdatedAt(limits.updatedAt());
        return toLimits(limitsRepository.save(entity));
    }

    private void publishUpdated(TenantLimits limits) {
        eventPublisher.publishEvent(new TenantLimitsUpdatedEvent(
                limits.tenantId(),
                limits.planId(),
                limits.maxProducts(),
                limits.maxAssets(),
                limits.maxStorageBytes(),
                limits.maxAdminUsers(),
                limits.maxDomains(),
                limits.updatedAt()));
    }

    private TenantLimits toLimits(TenantPlanLimitsEntity entity) {
        return new TenantLimits(
                entity.getTenantId(),
                entity.getPlanId(),
                entity.getMaxProducts(),
                entity.getMaxAssets(),
                entity.getMaxStorageBytes(),
                entity.getMaxAdminUsers(),
                entity.getMaxDomains(),
                entity.getUpdatedAt());
    }
}
