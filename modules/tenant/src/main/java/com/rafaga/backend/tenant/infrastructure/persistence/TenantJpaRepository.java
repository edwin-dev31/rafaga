package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.Tenant;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TenantJpaRepository extends JpaRepository<Tenant, String>, JpaSpecificationExecutor<Tenant> {
    List<Tenant> findByStatus(TenantStatus status);
}
