package com.rafaga.backend.media.infrastructure.persistence.repository;

import com.rafaga.backend.media.infrastructure.persistence.entity.TenantMediaQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMediaQuotaRepository extends JpaRepository<TenantMediaQuotaEntity, String> {

    TenantMediaQuotaEntity findByTenantId(String tenantId);
}
