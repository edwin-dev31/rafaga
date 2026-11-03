package com.rafaga.backend.tenant.infrastructure.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.cache.TenantConfigCache;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JpaTenantRegistryTest {
    @Test
    void invalidatesTheTenantCacheAfterRegisteringAndUpdating() {
        TenantConfigurationStore store = mock(TenantConfigurationStore.class);
        TenantConfigCache cache = mock(TenantConfigCache.class);
        JpaTenantRegistry registry = new JpaTenantRegistry(store, cache);
        TenantConfig tenant = tenantConfig();

        registry.register(tenant);
        registry.update(tenant);

        verify(store, times(2)).save(tenant);
        verify(cache, times(2)).invalidate(tenant.tenantId());
    }

    private TenantConfig tenantConfig() {
        return new TenantConfig(
                "tenant-1",
                "Demo Store",
                List.of("demo.test"),
                DeploymentMode.SHARED_DB,
                null,
                "starter",
                TenantStatus.ACTIVE,
                Map.of());
    }
}
