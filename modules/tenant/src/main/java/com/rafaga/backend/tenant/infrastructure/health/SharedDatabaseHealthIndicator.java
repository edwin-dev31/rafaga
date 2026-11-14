package com.rafaga.backend.tenant.infrastructure.health;

import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SharedDatabaseHealthIndicator implements HealthIndicator {
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final TenantAwareRoutingDataSource dataSource;

    public SharedDatabaseHealthIndicator(TenantAwareRoutingDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return Health.down().withDetail("database", "shared").build();
            }
            return Health.up()
                    .withDetail("database", "shared")
                    .withDetail("pool", poolDetails(dataSource.sharedDataSource()))
                    .build();
        } catch (SQLException exception) {
            return Health.down(exception).withDetail("database", "shared").build();
        }
    }

    private Map<String, Integer> poolDetails(HikariDataSource pool) {
        return Map.of(
                "active", pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getActiveConnections(),
                "idle", pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getIdleConnections(),
                "total", pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getTotalConnections(),
                "maximum", pool.getMaximumPoolSize());
    }
}
