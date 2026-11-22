package com.rafaga.backend.tenant.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantTargetTransitionRepository extends JpaRepository<TenantTargetTransitionEntity, String> {
    List<TenantTargetTransitionEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
