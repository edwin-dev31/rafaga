package com.rafaga.backend.infrastructure.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class SystemAdministrationControllerTest {
    @Test
    void reportsOnlyComponentStatusesAndAggregatesDegradedDependencies() {
        HealthIndicator shared = mock(HealthIndicator.class);
        HealthIndicator dedicated = mock(HealthIndicator.class);
        HealthIndicator redis = mock(HealthIndicator.class);
        HealthIndicator keycloak = mock(HealthIndicator.class);
        when(shared.health()).thenReturn(Health.up().withDetail("url", "jdbc:secret").build());
        when(dedicated.health()).thenReturn(Health.up().build());
        when(redis.health()).thenReturn(Health.status("DEGRADED").build());
        when(keycloak.health()).thenReturn(Health.up().build());

        SystemAdministrationController controller = new SystemAdministrationController(Map.of(
                "sharedDatabaseHealthIndicator", shared,
                "dedicatedDatabaseHealthIndicator", dedicated,
                "redisHealthIndicator", redis,
                "keycloakHealthIndicator", keycloak), new ConcurrentMapCacheManager("products"));

        var response = controller.health();

        assertThat(response.status()).isEqualTo("DEGRADED");
        assertThat(response.components()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "sharedDatabase", "UP",
                "dedicatedDatabases", "UP",
                "redis", "DEGRADED",
                "keycloak", "UP"));
    }

    @Test
    void clearsOnlyRecognizedCacheRegions() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("products");
        cacheManager.getCache("products").put("tenant-a:product-a", "cached");
        SystemAdministrationController controller = new SystemAdministrationController(Map.of(), cacheManager);

        controller.invalidateCache("products");

        assertThat(cacheManager.getCache("products").get("tenant-a:product-a")).isNull();
        assertThatThrownBy(() -> controller.invalidateCache("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
