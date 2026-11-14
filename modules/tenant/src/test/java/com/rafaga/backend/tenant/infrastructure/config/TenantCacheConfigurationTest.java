package com.rafaga.backend.tenant.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class TenantCacheConfigurationTest {
    @Test
    void configuresTheRequiredRedisCacheRegions() {
        TenantCacheConfiguration configuration = new TenantCacheConfiguration();
        RedisCacheManager cacheManager = (RedisCacheManager) configuration.cacheManager(
                mock(RedisConnectionFactory.class));

        Set.of("tenants", "feature-flags", "products", "categories")
                .forEach(cacheManager::getCache);

        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrderElementsOf(
                        Set.of("tenants", "feature-flags", "products", "categories"));
    }

    @Test
    void exposesAnErrorHandlerSoRedisIsOnlyAnOptimization() {
        CacheErrorHandler errorHandler = new TenantCacheConfiguration().errorHandler();

        assertThat(errorHandler).isNotNull();
    }
}
