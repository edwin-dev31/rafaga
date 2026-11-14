package com.rafaga.backend.tenant.infrastructure.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis accelerates requests but does not make the business source of truth unavailable. */
@Component
public class RedisHealthIndicator implements HealthIndicator {
    private final StringRedisTemplate redis;

    public RedisHealthIndicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Health health() {
        try {
            String response = redis.execute((RedisCallback<String>) connection -> connection.ping());
            if ("PONG".equalsIgnoreCase(response)) {
                return Health.up().build();
            }
            return Health.status("DEGRADED").withDetail("reason", "Unexpected Redis ping response").build();
        } catch (Exception exception) {
            return Health.status("DEGRADED")
                    .withDetail("reason", "Redis is unavailable; requests will bypass cache")
                    .build();
        }
    }
}
