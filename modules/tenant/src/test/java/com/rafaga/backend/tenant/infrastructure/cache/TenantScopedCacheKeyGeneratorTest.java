package com.rafaga.backend.tenant.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantScopedCacheKeyGeneratorTest {
    private final TenantScopedCacheKeyGenerator generator = new TenantScopedCacheKeyGenerator();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void prefixesEveryKeyWithTheCurrentTenant() throws NoSuchMethodException {
        TenantContext.setCurrentTenantId("tenant-a");
        UUID productId = UUID.randomUUID();
        Method method = getClass().getDeclaredMethod("cachedProduct", UUID.class);

        assertThat(generator.generate(this, method, productId))
                .isEqualTo("tenant-a:" + productId);
    }

    @Test
    void usesTheTenantAsTheKeyForAParameterlessQuery() throws NoSuchMethodException {
        TenantContext.setCurrentTenantId("tenant-b");
        Method method = getClass().getDeclaredMethod("cachedCategoryTree");

        assertThat(generator.generate(this, method)).isEqualTo("tenant-b");
    }

    @SuppressWarnings("unused")
    private void cachedProduct(UUID productId) {}

    @SuppressWarnings("unused")
    private void cachedCategoryTree() {}
}
