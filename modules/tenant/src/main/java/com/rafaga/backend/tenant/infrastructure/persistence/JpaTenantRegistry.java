package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.infrastructure.cache.TenantConfigCache;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class JpaTenantRegistry implements TenantRegistry {
    private final TenantConfigurationStore store;
    private final TenantConfigCache cache;

    JpaTenantRegistry(TenantConfigurationStore store, TenantConfigCache cache) {
        this.store = store;
        this.cache = cache;
    }

    @Override
    public Optional<TenantConfig> findById(String tenantId) {
        return cache.get(tenantId);
    }

    @Override
    public Optional<TenantConfig> findByDomain(String domain) {
        return store.findByDomain(domain).flatMap(config -> cache.get(config.tenantId()));
    }

    @Override
    public List<TenantConfig> findAll() {
        return store.findAll();
    }

    @Override
    public void register(TenantConfig config) {
        store.save(config);
        cache.invalidate(config.tenantId());
    }

    @Override
    public void update(TenantConfig config) {
        store.save(config);
        cache.invalidate(config.tenantId());
    }

    @Override
    public void invalidateCache(String tenantId) {
        cache.invalidate(tenantId);
    }
}
