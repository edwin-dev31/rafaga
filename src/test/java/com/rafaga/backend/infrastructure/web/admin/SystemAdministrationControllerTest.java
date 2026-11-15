package com.rafaga.backend.infrastructure.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
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
                "keycloakHealthIndicator", keycloak), new ConcurrentMapCacheManager("products"),
                mock(TenantRegistry.class), mock(TenantAwareRoutingDataSource.class));

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
        SystemAdministrationController controller = new SystemAdministrationController(Map.of(), cacheManager,
                mock(TenantRegistry.class), mock(TenantAwareRoutingDataSource.class));

        controller.invalidateCache("products");

        assertThat(cacheManager.getCache("products").get("tenant-a:product-a")).isNull();
        assertThatThrownBy(() -> controller.invalidateCache("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsOnlyBoundedPlatformStateInTheOperationsOverview() {
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantAwareRoutingDataSource dataSource = mock(TenantAwareRoutingDataSource.class);
        HikariDataSource pool = mock(HikariDataSource.class);
        when(dataSource.sharedDataSource()).thenReturn(pool);
        when(pool.getMaximumPoolSize()).thenReturn(50);
        when(registry.findAll()).thenReturn(List.of(
                new TenantConfig("a", "A", List.of("a.example.test"), DeploymentMode.SHARED_DB,
                        null, "basic", TenantStatus.ACTIVE, Map.of()),
                new TenantConfig("b", "B", List.of("b.example.test", "shop-b.example.test"),
                        DeploymentMode.DEDICATED_DB, null, "pro", TenantStatus.INACTIVE, Map.of())));
        SystemAdministrationController controller = new SystemAdministrationController(Map.of(),
                new ConcurrentMapCacheManager("products", "tenants"), registry, dataSource);

        var overview = controller.overview();

        assertThat(overview.tenants().total()).isEqualTo(2);
        assertThat(overview.tenants().active()).isEqualTo(1);
        assertThat(overview.tenants().dedicatedDatabases()).isEqualTo(1);
        assertThat(overview.tenants().domains()).isEqualTo(3);
        assertThat(overview.sharedDatabasePool().maximum()).isEqualTo(50);
        assertThat(overview.cache().configuredRegions()).contains("products", "tenants");
    }
}
