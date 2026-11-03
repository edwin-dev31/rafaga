package com.rafaga.backend.tenant.application.port;

import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.util.List;
import java.util.Optional;

public interface TenantRegistry {
    Optional<TenantConfig> findById(String tenantId);

    Optional<TenantConfig> findByDomain(String domain);

    List<TenantConfig> findAll();

    void register(TenantConfig config);

    void update(TenantConfig config);

    void invalidateCache(String tenantId);
}
