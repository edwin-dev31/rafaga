package com.rafaga.backend.tenant.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.infrastructure.config.TenantCacheConfiguration;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(RedisCacheIntegrationTest.CacheTestConfiguration.class)
class RedisCacheIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private CacheProbe cacheProbe;

    @org.springframework.beans.factory.annotation.Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCacheRegions() {
        List.of("tenants", "feature-flags", "products", "categories").forEach(cacheName ->
                cacheManager.getCache(cacheName).clear());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void cachesPerTenantAndEvictsTheAffectedProduct() {
        TenantContext.setCurrentTenantId("tenant-a");
        assertThat(cacheProbe.load("product-1")).isEqualTo("value-1");
        assertThat(cacheProbe.load("product-1")).isEqualTo("value-1");
        assertThat(cacheProbe.invocations()).isEqualTo(1);

        TenantContext.setCurrentTenantId("tenant-b");
        assertThat(cacheProbe.load("product-1")).isEqualTo("value-2");
        assertThat(cacheProbe.invocations()).isEqualTo(2);

        cacheProbe.evict("product-1");
        assertThat(cacheProbe.load("product-1")).isEqualTo("value-3");
        assertThat(cacheProbe.invocations()).isEqualTo(3);
    }

    @Test
    void preservesTenantConfigTypeWhenReadingFromRedis() {
        TenantConfig expected = new TenantConfig("tenant-a", "Store A", List.of("a.test"),
                DeploymentMode.SHARED_DB, null, "starter", TenantStatus.ACTIVE,
                Map.of("catalog", true));

        assertThat(cacheProbe.tenantConfig(expected.tenantId())).isEqualTo(expected);
        assertThat(cacheProbe.tenantConfig(expected.tenantId())).isEqualTo(expected);
        assertThat(cacheProbe.tenantConfigInvocations()).isEqualTo(1);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(TenantCacheConfiguration.class)
    static class CacheTestConfiguration {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("localhost", 6380);
        }

        @Bean
        TenantScopedCacheKeyGenerator tenantScopedCacheKeyGenerator() {
            return new TenantScopedCacheKeyGenerator();
        }

        @Bean
        CacheProbe cacheProbe() {
            return new CacheProbe();
        }
    }

    static class CacheProbe {
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicInteger tenantConfigInvocations = new AtomicInteger();

        @Cacheable(cacheNames = "products", keyGenerator = "tenantScopedCacheKeyGenerator")
        public String load(String productId) {
            return "value-" + invocations.incrementAndGet();
        }

        @CacheEvict(cacheNames = "products", key = "T(com.rafaga.backend.common.application.tenant.TenantContext).getCurrentTenantId() + ':' + #p0")
        public void evict(String productId) {}

        @Cacheable(cacheNames = "tenants", key = "'id:' + #p0")
        public TenantConfig tenantConfig(String tenantId) {
            tenantConfigInvocations.incrementAndGet();
            return new TenantConfig(tenantId, "Store A", List.of("a.test"),
                    DeploymentMode.SHARED_DB, null, "starter", TenantStatus.ACTIVE,
                    Map.of("catalog", true));
        }

        int invocations() {
            return invocations.get();
        }

        int tenantConfigInvocations() {
            return tenantConfigInvocations.get();
        }
    }
}
