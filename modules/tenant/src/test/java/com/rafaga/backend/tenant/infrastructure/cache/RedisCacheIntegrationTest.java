package com.rafaga.backend.tenant.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.infrastructure.config.TenantCacheConfiguration;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(RedisCacheIntegrationTest.CacheTestConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class RedisCacheIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private CacheProbe cacheProbe;

    @BeforeEach
    void resetProbe() {
        cacheProbe.reset();
        TenantContext.clear();
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void cachesPerTenantAndEvictsTheAffectedProduct() {
        String productId = "product-" + UUID.randomUUID();
        TenantContext.setCurrentTenantId("tenant-a");
        assertThat(cacheProbe.load(productId)).isEqualTo("value-1");
        assertThat(cacheProbe.load(productId)).isEqualTo("value-1");
        assertThat(cacheProbe.invocations()).isEqualTo(1);

        TenantContext.setCurrentTenantId("tenant-b");
        assertThat(cacheProbe.load(productId)).isEqualTo("value-2");
        assertThat(cacheProbe.invocations()).isEqualTo(2);

    }

    @Test
    void preservesTenantConfigTypeWhenReadingFromRedis() {
        TenantConfig expected = new TenantConfig("tenant-" + UUID.randomUUID(), "Store A", List.of("a.test"),
                DeploymentMode.SHARED_DB, null, "starter", TenantStatus.ACTIVE,
                Map.of("catalog", true));

        assertThat(cacheProbe.tenantConfig(expected.tenantId())).isEqualTo(expected);
        assertThat(cacheProbe.tenantConfig(expected.tenantId())).isEqualTo(expected);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(TenantCacheConfiguration.class)
    static class CacheTestConfiguration {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("localhost", 6380);
            configuration.setDatabase(15);
            return new LettuceConnectionFactory(configuration);
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

        @Cacheable(cacheNames = "products", keyGenerator = "tenantScopedCacheKeyGenerator")
        public String load(String productId) {
            return "value-" + invocations.incrementAndGet();
        }

        @Cacheable(cacheNames = "tenant-config-type-tests", key = "'id:' + #p0")
        public TenantConfig tenantConfig(String tenantId) {
            return new TenantConfig(tenantId, "Store A", List.of("a.test"),
                    DeploymentMode.SHARED_DB, null, "starter", TenantStatus.ACTIVE,
                    Map.of("catalog", true));
        }

        int invocations() {
            return invocations.get();
        }

        void reset() {
            invocations.set(0);
        }
    }
}
