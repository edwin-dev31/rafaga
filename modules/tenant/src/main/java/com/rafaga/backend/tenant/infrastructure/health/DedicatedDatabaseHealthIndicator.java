package com.rafaga.backend.tenant.infrastructure.health;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DedicatedDatabaseHealthIndicator implements HealthIndicator {
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final TenantRegistry tenantRegistry;
    private final TenantAwareRoutingDataSource dataSource;

    public DedicatedDatabaseHealthIndicator(TenantRegistry tenantRegistry,
            TenantAwareRoutingDataSource dataSource) {
        this.tenantRegistry = tenantRegistry;
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        Map<String, Map<String, Object>> tenantStatuses = new LinkedHashMap<>();
        boolean unavailable = false;
        for (var tenant : tenantRegistry.findAll()) {
            if (tenant.status() != TenantStatus.ACTIVE
                    || tenant.deploymentMode() != DeploymentMode.DEDICATED_DB) {
                continue;
            }
            try {
                HikariDataSource pool = dataSource.dedicatedDataSource(tenant.tenantId());
                try (Connection connection = pool.getConnection()) {
                boolean available = connection.isValid(VALIDATION_TIMEOUT_SECONDS);
                tenantStatuses.put(tenant.tenantId(), poolDetails(pool, available ? "UP" : "DOWN"));
                unavailable |= !available;
                }
            } catch (Exception exception) {
                tenantStatuses.put(tenant.tenantId(), Map.of("status", "DOWN"));
                unavailable = true;
            }
        }

        Health.Builder health = unavailable ? Health.down() : Health.up();
        return health
                .withDetail("checkedTenants", tenantStatuses.size())
                .withDetail("tenants", tenantStatuses)
                .build();
    }

    private Map<String, Object> poolDetails(HikariDataSource pool, String status) {
        return Map.of(
                "status", status,
                "active", pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getActiveConnections(),
                "idle", pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getIdleConnections(),
                "total", pool.getHikariPoolMXBean() == null ? 0 : pool.getHikariPoolMXBean().getTotalConnections(),
                "maximum", pool.getMaximumPoolSize());
    }
}
