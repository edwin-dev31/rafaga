package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.Tenant;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TenantJpaRepository extends JpaRepository<Tenant, String> {
    Optional<Tenant> findByDomainsContaining(String domain);
    List<Tenant> findByStatus(TenantStatus status);
}
