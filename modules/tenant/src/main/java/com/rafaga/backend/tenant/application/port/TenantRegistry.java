package com.rafaga.backend.tenant.application.port;

import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public interface TenantRegistry {
    Optional<TenantConfig> findById(String tenantId);

    Optional<TenantConfig> findByDomain(String domain);

    List<TenantConfig> findAll();

    default Page<TenantConfig> search(TenantSearchFilter filter, Pageable pageable) {
        List<TenantConfig> all = findAll();
        return new PageImpl<>(all, pageable, all.size());
    }

    void register(TenantConfig config);

    void update(TenantConfig config);

    void invalidateCache(String tenantId);
}
