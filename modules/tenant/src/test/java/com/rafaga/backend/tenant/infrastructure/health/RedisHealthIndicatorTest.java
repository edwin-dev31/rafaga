package com.rafaga.backend.tenant.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHealthIndicatorTest {
    @Test
    void reportsUpAfterAPong() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn("PONG").when(redis).execute(any(RedisCallback.class));

        assertThat(new RedisHealthIndicator(redis).health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void reportsDegradedWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("unavailable"))
                .when(redis).execute(any(RedisCallback.class));

        assertThat(new RedisHealthIndicator(redis).health().getStatus().getCode()).isEqualTo("DEGRADED");
    }
}
