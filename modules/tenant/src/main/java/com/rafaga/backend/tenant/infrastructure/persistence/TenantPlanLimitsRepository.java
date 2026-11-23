package com.rafaga.backend.tenant.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPlanLimitsRepository extends JpaRepository<TenantPlanLimitsEntity, String> {
}
