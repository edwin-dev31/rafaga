package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.application.service.TenantDirectoryService;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.FeatureFlag;
import com.rafaga.backend.tenant.domain.model.Tenant;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TenantConfigurationStore {
    private final TenantJpaRepository tenantRepository;
    private final TenantDomainControlRepository domainControlRepository;
    private final FeatureFlagJpaRepository featureFlagRepository;
    private final Clock clock;

    public TenantConfigurationStore(
            TenantJpaRepository tenantRepository,
            TenantDomainControlRepository domainControlRepository,
            FeatureFlagJpaRepository featureFlagRepository) {
        this.tenantRepository = tenantRepository;
        this.domainControlRepository = domainControlRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public Optional<TenantConfig> findById(String tenantId) {
        return tenantRepository.findById(tenantId)
                .map(this::toConfig);
    }

    @Transactional(readOnly = true)
    public Optional<TenantConfig> findByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return domainControlRepository.findByDomain(domain.toLowerCase(Locale.ROOT))
                .filter(control -> control.getStatus() == DomainStatus.ACTIVE)
                .flatMap(control -> tenantRepository.findById(control.getTenantId()))
                .map(this::toConfig);
    }

    @Transactional(readOnly = true)
    public List<TenantConfig> findAll() {
        return tenantRepository.findAll().stream()
                .map(this::toConfig)
                .toList();
    }

    @Transactional
    public void save(TenantConfig config) {
        Instant now = clock.instant();
        Tenant tenant = tenantRepository.findById(config.tenantId()).orElse(null);
        if (tenant == null) {
            tenant = new Tenant(
                    config.tenantId(),
                    config.name(),
                    config.deploymentMode(),
                    secretReference(config.databaseConfig()),
                    config.planId(),
                    config.status(),
                    now);
        } else {
            tenant.update(
                    config.name(),
                    config.deploymentMode(),
                    secretReference(config.databaseConfig()),
                    config.planId(),
                    config.status(),
                    now);
        }
        tenantRepository.save(tenant);
        syncDomains(config, now);
        syncFlags(config);
    }

    private TenantConfig toConfig(Tenant tenant) {
        Instant now = clock.instant();
        List<String> domains = domainControlRepository.findByTenantIdOrderByDomain(tenant.getId())
                .stream()
                .map(TenantDomainControl::getDomain)
                .toList();
        Map<String, Boolean> featureFlags = featureFlagRepository
                .findAllByTenantId(tenant.getId())
                .stream()
                .collect(Collectors.toUnmodifiableMap(FeatureFlag::getFeatureName, flag -> flag.isEnabledAt(now)));
        String databaseConfigId = tenant.getDatabaseConfigId();
        return new TenantConfig(
                tenant.getId(),
                tenant.getName(),
                domains,
                tenant.getDeploymentMode(),
                databaseConfigId == null ? null : new DatabaseConfig(databaseConfigId),
                tenant.getPlanId(),
                tenant.getStatus(),
                featureFlags);
    }

    private void syncDomains(TenantConfig config, Instant now) {
        Set<String> desired = config.domains().stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (desired.isEmpty()) {
            throw new IllegalArgumentException("Tenant must have at least one domain");
        }
        Map<String, TenantDomainControl> existing = domainControlRepository
                .findByTenantIdOrderByDomain(config.tenantId())
                .stream()
                .collect(Collectors.toMap(TenantDomainControl::getDomain, Function.identity()));
        for (String domain : desired) {
            if (existing.remove(domain) == null) {
                domainControlRepository.save(new TenantDomainControl(
                        config.tenantId(),
                        domain,
                        DomainStatus.ACTIVE,
                        null,
                        TenantDirectoryService.DEFAULT_SHARED_POOL_TARGET_ID,
                        now));
            }
        }
        if (!existing.isEmpty()) {
            domainControlRepository.deleteAll(existing.values());
        }
    }

    private void syncFlags(TenantConfig config) {
        Instant now = clock.instant();
        Map<String, FeatureFlag> existing = featureFlagRepository
                .findAllByTenantId(config.tenantId())
                .stream()
                .collect(Collectors.toMap(FeatureFlag::getFeatureName, Function.identity()));
        for (Map.Entry<String, Boolean> entry : config.featureFlags().entrySet()) {
            FeatureFlag flag = existing.remove(entry.getKey());
            if (flag == null) {
                featureFlagRepository.save(new FeatureFlag(
                        java.util.UUID.randomUUID().toString(),
                        config.tenantId(),
                        entry.getKey(),
                        entry.getValue(),
                        now,
                        null));
            } else {
                flag.update(entry.getValue(), now, null);
            }
        }
        featureFlagRepository.deleteAll(existing.values());
    }

    private String secretReference(DatabaseConfig databaseConfig) {
        if (databaseConfig == null) {
            return null;
        }
        return databaseConfig.secretReference();
    }
}
