package com.rafaga.backend.tenant.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

class RedisCacheErrorHandlerTest {
    @Test
    void logsAndSuppressesAllCacheOperationFailures() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("products");
        RedisCacheErrorHandler handler = new RedisCacheErrorHandler();
        RuntimeException failure = new RuntimeException("Redis unavailable");

        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "tenant-a:product"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(failure, cache, "tenant-a:product", "value"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(failure, cache, "tenant-a:product"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(failure, cache))
                .doesNotThrowAnyException();
    }
}
