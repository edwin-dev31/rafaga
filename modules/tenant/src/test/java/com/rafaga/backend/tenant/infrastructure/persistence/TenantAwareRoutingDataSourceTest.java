package com.rafaga.backend.tenant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantAwareRoutingDataSourceTest {
    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @Test
    void choosesSystemSharedAndDedicatedLookupKeys() {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findById("shared")).thenReturn(Optional.of(tenant("shared", DeploymentMode.SHARED_DB)));
        when(registry.findById("dedicated")).thenReturn(Optional.of(tenant("dedicated", DeploymentMode.DEDICATED_DB)));
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/unused");
        config.setInitializationFailTimeout(-1);
        HikariDataSource shared = new HikariDataSource(config);
        TenantAwareRoutingDataSource routing = new TenantAwareRoutingDataSource(registry, mock(TenantDatabaseCredentialsProvider.class), shared);

        assertThat(routing.determineCurrentLookupKey()).isEqualTo(TenantAwareRoutingDataSource.SYSTEM_KEY);
        TenantContext.setCurrentTenantId("shared");
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(TenantAwareRoutingDataSource.SHARED_KEY);
        TenantContext.setCurrentTenantId("dedicated");
        assertThat(routing.determineCurrentLookupKey()).isEqualTo("tenant-dedicated");

        routing.close();
    }

    private TenantConfig tenant(String id, DeploymentMode mode) {
        return new TenantConfig(id, id, List.of(id + ".test"), mode, null, "test", TenantStatus.ACTIVE, Map.of());
    }
}
