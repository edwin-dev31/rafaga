package com.rafaga.backend.tenant.infrastructure.cache;

import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantConfigurationStore;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class TenantConfigCache {
    public static final String TENANTS_CACHE = "tenants";

    private final TenantConfigurationStore store;

    public TenantConfigCache(TenantConfigurationStore store) {
        this.store = store;
    }

    @Cacheable(cacheNames = TENANTS_CACHE, key = "'id:' + #p0")
    public Optional<TenantConfig> get(String tenantId) {
        return store.findById(tenantId);
    }

    @Cacheable(cacheNames = TENANTS_CACHE, key = "'domain:' + #p0")
    public Optional<TenantConfig> getByDomain(String domain) {
        return store.findByDomain(domain);
    }

    @CacheEvict(cacheNames = TENANTS_CACHE, allEntries = true)
    public void invalidate(String tenantId) {}
}
