package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.FeatureFlag;
import com.rafaga.backend.tenant.domain.model.Tenant;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TenantConfigurationStore {
    private final TenantJpaRepository tenantRepository;
    private final FeatureFlagJpaRepository featureFlagRepository;
    private final Clock clock;

    public TenantConfigurationStore(
            TenantJpaRepository tenantRepository,
            FeatureFlagJpaRepository featureFlagRepository) {
        this.tenantRepository = tenantRepository;
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
        return tenantRepository.findByDomainsContaining(domain.toLowerCase())
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
                    config.domains(),
                    config.deploymentMode(),
                    secretReference(config.databaseConfig()),
                    config.planId(),
                    config.status(),
                    now);
        } else {
            tenant.update(
                    config.name(),
                    config.domains(),
                    config.deploymentMode(),
                    secretReference(config.databaseConfig()),
                    config.planId(),
                    config.status(),
                    now);
        }
        tenantRepository.save(tenant);
        syncFlags(config);
    }

    private TenantConfig toConfig(Tenant tenant) {
        Instant now = clock.instant();
        Map<String, Boolean> featureFlags = featureFlagRepository
                .findAllByTenantId(tenant.getId())
                .stream()
                .collect(Collectors.toUnmodifiableMap(FeatureFlag::getFeatureName, flag -> flag.isEnabledAt(now)));
        String databaseConfigId = tenant.getDatabaseConfigId();
        return new TenantConfig(
                tenant.getId(),
                tenant.getName(),
                tenant.getDomains(),
                tenant.getDeploymentMode(),
                databaseConfigId == null ? null : new DatabaseConfig(databaseConfigId),
                tenant.getPlanId(),
                tenant.getStatus(),
                featureFlags);
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
