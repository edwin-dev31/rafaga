package com.rafaga.backend.tenant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.DatabaseConnectionDetails;
import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TenantAwareRoutingDataSourceIntegrationTest {
    @Container
    static final PostgreSQLContainer SHARED_POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer DEDICATED_POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void routesSharedAndDedicatedTenantsToTheirOwnDatabases() throws Exception {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findById("shared")).thenReturn(Optional.of(tenant("shared", DeploymentMode.SHARED_DB, null)));
        DatabaseConfig dedicatedSecret = new DatabaseConfig("dedicated-tenant-secret");
        when(registry.findById("dedicated"))
                .thenReturn(Optional.of(tenant("dedicated", DeploymentMode.DEDICATED_DB, dedicatedSecret)));
        TenantDatabaseCredentialsProvider credentials = config -> new DatabaseConnectionDetails(
                DEDICATED_POSTGRES.getJdbcUrl(), DEDICATED_POSTGRES.getUsername(), DEDICATED_POSTGRES.getPassword());
        TenantAwareRoutingDataSource routing = new TenantAwareRoutingDataSource(
                registry, credentials, dataSourceFor(SHARED_POSTGRES));

        TenantContext.setCurrentTenantId("shared");
        assertThat(currentDatabase(routing.getDataSourceForCurrentTenant().getConnection()))
                .isEqualTo(SHARED_POSTGRES.getDatabaseName());

        TenantContext.setCurrentTenantId("dedicated");
        assertThat(currentDatabase(routing.getDataSourceForCurrentTenant().getConnection()))
                .isEqualTo(DEDICATED_POSTGRES.getDatabaseName());
        assertThat(routing.dedicatedDataSources()).containsKey("tenant-dedicated");

        routing.close();
    }

    private HikariDataSource dataSourceFor(PostgreSQLContainer postgres) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        return new HikariDataSource(config);
    }

    private String currentDatabase(Connection connection) throws Exception {
        try (connection; Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select current_database()")) {
            result.next();
            return result.getString(1);
        }
    }

    private TenantConfig tenant(String id, DeploymentMode mode, DatabaseConfig databaseConfig) {
        return new TenantConfig(id, id, List.of(id + ".test"), mode, databaseConfig, "test", TenantStatus.ACTIVE, Map.of());
    }
}
