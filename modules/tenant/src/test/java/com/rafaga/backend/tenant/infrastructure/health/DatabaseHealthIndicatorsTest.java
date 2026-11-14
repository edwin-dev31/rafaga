package com.rafaga.backend.tenant.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseHealthIndicatorsTest {
    @Test
    void marksTheSharedDatabaseDownWhenConnectionValidationFails() throws Exception {
        TenantAwareRoutingDataSource dataSource = mock(TenantAwareRoutingDataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        assertThat(new SharedDatabaseHealthIndicator(dataSource).health().getStatus().getCode())
                .isEqualTo("DOWN");
    }

    @Test
    void marksTheDedicatedDatabaseContributorDownWhenAnyActiveTenantIsUnavailable() throws Exception {
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantAwareRoutingDataSource dataSource = mock(TenantAwareRoutingDataSource.class);
        HikariDataSource dedicatedPool = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        TenantConfig tenant = new TenantConfig("tenant-a", "Store A", List.of("a.test"),
                DeploymentMode.DEDICATED_DB, mock(), "premium", TenantStatus.ACTIVE, Map.of());
        when(registry.findAll()).thenReturn(List.of(tenant));
        when(dataSource.dedicatedDataSource("tenant-a")).thenReturn(dedicatedPool);
        when(dedicatedPool.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        var health = new DedicatedDatabaseHealthIndicator(registry, dataSource).health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("checkedTenants", 1);
    }
}
