package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.FeatureFlag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FeatureFlagJpaRepository extends JpaRepository<FeatureFlag, String> {
    Optional<FeatureFlag> findByTenantIdAndFeatureName(String tenantId, String featureName);
    List<FeatureFlag> findAllByTenantId(String tenantId);
}
