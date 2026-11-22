package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.TenantOperationsQuery;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantOperationsQueryService implements TenantOperationsQuery {
    private final DeploymentTargetRepository targetRepository;
    private final TenantDomainControlRepository domainRepository;

    public TenantOperationsQueryService(
            DeploymentTargetRepository targetRepository,
            TenantDomainControlRepository domainRepository) {
        this.targetRepository = targetRepository;
        this.domainRepository = domainRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformRoutingSummary platformSummary() {
        List<DeploymentTargetEntity> targets = targetRepository.findAll();
        long healthy = targets.stream().filter(t -> t.getStatus() == DeploymentTargetStatus.HEALTHY).count();
        long unhealthy = targets.stream().filter(t -> t.getStatus() == DeploymentTargetStatus.UNHEALTHY).count();
        long draining = targets.stream().filter(t -> t.getStatus() == DeploymentTargetStatus.DRAINING).count();
        long saturated = targets.stream().filter(t -> t.getCapacity() == DeploymentTargetCapacity.FULL).count();

        Map<String, Long> domainCounts = new LinkedHashMap<>();
        for (TenantDomainControl domain : domainRepository.findAll()) {
            String key = domain.getStatus() == null ? "UNKNOWN" : domain.getStatus().name();
            domainCounts.merge(key, 1L, Long::sum);
        }

        return new PlatformRoutingSummary(
                targets.size(),
                healthy,
                unhealthy,
                draining,
                saturated,
                Map.copyOf(domainCounts));
    }

    @Override
    @Transactional(readOnly = true)
    public TenantRouteOperationsSummary tenantSummary(String tenantId) {
        Map<String, DeploymentTargetEntity> targetsById = targetRepository.findAll().stream()
                .collect(Collectors.toMap(DeploymentTargetEntity::getId, Function.identity(), (a, b) -> a));

        List<DomainLifecycleSummary> domains = domainRepository.findByTenantIdOrderByDomain(tenantId).stream()
                .map(domain -> {
                    DeploymentTargetEntity target = domain.getTargetId() == null
                            ? null
                            : targetsById.get(domain.getTargetId());
                    return new DomainLifecycleSummary(
                            domain.getDomain(),
                            domain.getStatus() == null ? "UNKNOWN" : domain.getStatus().name(),
                            domain.getTargetId(),
                            target == null ? "UNKNOWN" : target.getTargetType().name(),
                            target == null ? "UNKNOWN" : target.getStatus().name(),
                            target == null ? "UNKNOWN" : target.getCapacity().name(),
                            domain.getLastFailureCode() != null && !domain.getLastFailureCode().isBlank());
                })
                .toList();

        return new TenantRouteOperationsSummary(domains);
    }
}
