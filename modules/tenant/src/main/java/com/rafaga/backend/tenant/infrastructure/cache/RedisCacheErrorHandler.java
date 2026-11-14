package com.rafaga.backend.tenant.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Treats Redis as an optimization: cache failures must not interrupt a business request.
 */
public final class RedisCacheErrorHandler implements CacheErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache read failed for {}. Continuing without cached data.", cache.getName(), exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache write failed for {}. Continuing without caching the result.", cache.getName(), exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache eviction failed for {}. Continuing with the source of truth.", cache.getName(), exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache clear failed for {}. Continuing with the source of truth.", cache.getName(), exception);
    }
}
