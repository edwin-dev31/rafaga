package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.DomainStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDomainControlRepository extends JpaRepository<TenantDomainControl, TenantDomainControlId> {
    Optional<TenantDomainControl> findByTenantIdAndDomain(String tenantId, String domain);
    Optional<TenantDomainControl> findByDomain(String domain);
    List<TenantDomainControl> findByTenantIdOrderByDomain(String tenantId);
    List<TenantDomainControl> findByStatus(DomainStatus status);
    List<TenantDomainControl> findByDirectoryVersionGreaterThanOrderByDirectoryVersion(long version);
}
