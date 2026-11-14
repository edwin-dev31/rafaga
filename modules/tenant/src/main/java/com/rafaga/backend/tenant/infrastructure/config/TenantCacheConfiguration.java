package com.rafaga.backend.tenant.infrastructure.config;

import com.rafaga.backend.tenant.infrastructure.cache.RedisCacheErrorHandler;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class TenantCacheConfiguration implements CachingConfigurer {
    private static final String PREFIX = "rafaga:";

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.rafaga.backend.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .build();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> PREFIX + cacheName + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(typeValidator)
                                .build()));

        Map<String, RedisCacheConfiguration> caches = Map.of(
                "tenants", defaults.entryTtl(Duration.ofMinutes(10)),
                "feature-flags", defaults.entryTtl(Duration.ofMinutes(5)),
                "products", defaults.entryTtl(Duration.ofMinutes(10)),
                "categories", defaults.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(caches)
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
}
