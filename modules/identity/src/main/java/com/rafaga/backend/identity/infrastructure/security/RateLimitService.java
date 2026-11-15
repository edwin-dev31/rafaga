package com.rafaga.backend.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed-window limiter. Redis is the shared counter; an intentionally bounded
 * local store remains stricter than the configured limit when Redis is down.
 */
@Service
class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "rafaga:rate-limit:";

    private final RateLimitProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Clock clock;
    private final Map<String, LocalWindow> fallbackWindows = new ConcurrentHashMap<>();
    private final AtomicBoolean fallbackActive = new AtomicBoolean();

    @Autowired
    RateLimitService(RateLimitProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(properties, redisTemplateProvider, Clock.systemUTC());
    }

    RateLimitService(RateLimitProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            Clock clock) {
        this.properties = properties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.clock = clock;
    }

    RateLimitDecision check(RateLimitRule rule, String hostname, String clientIdentity) {
        if (!properties.isEnabled()) {
            return RateLimitDecision.permitted();
        }
        int limit = rule.limit(properties);
        validateLimit(limit);
        String key = KEY_PREFIX + rule.nameValue() + ':' + hash(hostname + ':' + clientIdentity);
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                Long requests = redis.opsForValue().increment(key);
                if (requests == null) {
                    throw new IllegalStateException("Redis did not return a rate-limit counter");
                }
                if (requests == 1) {
                    redis.expire(key, properties.getWindow());
                }
                logRedisRecovery();
                return decision(requests, limit, properties.getWindow());
            }
        } catch (DataAccessException | IllegalStateException exception) {
            logFallback();
        }
        return localFallback(key, limit);
    }

    private RateLimitDecision localFallback(String key, int configuredLimit) {
        cleanupExpiredWindows();
        Instant now = clock.instant();
        Duration window = properties.getWindow();
        int stricterLimit = Math.max(1, configuredLimit / 2);
        LocalWindow windowValue = fallbackWindows.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new LocalWindow(1, now.plus(window));
            }
            return new LocalWindow(existing.requests() + 1, existing.expiresAt());
        });
        if (fallbackWindows.size() > properties.getMaxFallbackEntries()) {
            fallbackWindows.remove(key, windowValue);
            return RateLimitDecision.rejected(window);
        }
        return decision(windowValue.requests(), stricterLimit,
                Duration.between(now, windowValue.expiresAt()));
    }

    private RateLimitDecision decision(long requests, int limit, Duration retryAfter) {
        return requests <= limit ? RateLimitDecision.permitted() : RateLimitDecision.rejected(retryAfter);
    }

    private void cleanupExpiredWindows() {
        if (fallbackWindows.size() < 1_000) {
            return;
        }
        Instant now = clock.instant();
        fallbackWindows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void validateLimit(int limit) {
        if (limit < 1 || properties.getWindow().isNegative() || properties.getWindow().isZero()) {
            throw new IllegalStateException("Rate-limit configuration must use positive limits and window");
        }
    }

    private void logFallback() {
        if (fallbackActive.compareAndSet(false, true)) {
            log.warn("Redis rate-limit counter unavailable; using stricter local fallback");
        }
    }

    private void logRedisRecovery() {
        if (fallbackActive.compareAndSet(true, false)) {
            log.info("Redis rate-limit counter recovered");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
        static RateLimitDecision permitted() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision rejected(Duration retryAfter) {
            return new RateLimitDecision(false, Math.max(1, retryAfter.toSeconds()));
        }
    }

    private record LocalWindow(long requests, Instant expiresAt) {
    }
}
