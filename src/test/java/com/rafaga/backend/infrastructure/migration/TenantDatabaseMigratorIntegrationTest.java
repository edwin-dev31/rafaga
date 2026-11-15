package com.rafaga.backend.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.port.DatabaseConnectionDetails;
import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers
class TenantDatabaseMigratorIntegrationTest {
    @Container
    final PostgreSQLContainer shared = postgres();

    @Container
    final PostgreSQLContainer dedicated = postgres();

    @BeforeEach
    void createRestrictedRuntimeRole() throws Exception {
        try (Connection connection = connection(dedicated);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE dedicated_runtime LOGIN NOSUPERUSER NOBYPASSRLS "
                    + "PASSWORD 'dedicated_runtime'");
        }
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void migratesSharedAndDedicatedDatabasesAndPreservesIndependentHistory() throws Exception {
        Flyway sharedFlyway = flyway(shared);
        sharedFlyway.migrate();
        try (Connection connection = connection(shared);
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tenants (id, name, deployment_mode, database_config_id, plan_id, "
                    + "status, created_at, updated_at) VALUES "
                    + "('dedicated-a', 'Dedicated A', 'DEDICATED_DB', 'dedicated-secret', 'pro', "
                    + "'ACTIVE', NOW(), NOW())");
        }

        TenantDatabaseMigrator migrator = migrator("classpath:db/migration");
        new DatabaseMigrationConfiguration()
                .migrateSharedAndDedicatedDatabases(migrator)
                .migrate(sharedFlyway);

        assertThat(historyCount(shared)).isEqualTo(14);
        assertThat(historyCount(dedicated)).isEqualTo(14);
        assertThat(value(dedicated, "SELECT COUNT(*) FROM tenants WHERE id = 'dedicated-a'"))
                .isEqualTo(1);
        assertThat(value(dedicated, "SELECT COUNT(*) FROM tenants WHERE id = 'tenant-demo'"))
                .isZero();
        assertThat(value(dedicated, "SELECT COUNT(*) FROM pg_class WHERE relname = 'products' "
                + "AND relrowsecurity AND relforcerowsecurity")).isEqualTo(1);
        assertThat(value(dedicated, "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'products' "
                + "AND indexname = 'idx_products_tenant_id'")).isEqualTo(1);
        assertThat(value(dedicated, "SELECT COUNT(*) FROM pg_class WHERE relname IN "
                + "('tenants', 'tenant_domains', 'tenant_feature_flags') AND relrowsecurity")).isZero();
        try (Connection runtime = runtimeConnection();
                Statement statement = runtime.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM products")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
        assertThatThrownBy(() -> {
            try (Connection runtime = runtimeConnection();
                    Statement statement = runtime.createStatement()) {
                statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history");
            }
        }).isInstanceOf(java.sql.SQLException.class);

        new DatabaseMigrationConfiguration()
                .migrateSharedAndDedicatedDatabases(migrator)
                .migrate(sharedFlyway);
        assertThat(historyCount(shared)).isEqualTo(14);
        assertThat(historyCount(dedicated)).isEqualTo(14);
        assertThat(flyway(dedicated).migrate().migrationsExecuted).isZero();
    }

    @Test
    void reportsModuleAndVersionWhenDedicatedMigrationFails() {
        TenantDatabaseMigrator migrator = migrator(
                "classpath:db/migration", "classpath:db/failing-migration");

        assertThatThrownBy(() -> migrator.migrate(tenant()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dedicated-a")
                .hasMessageContaining("module/version")
                .hasMessageContaining("tenant/V13__broken.sql")
                .hasMessageContaining("V13__broken.sql");
        // The intentionally failing V13 migration stops before V14/V15.
        assertThat(historyCount(dedicated)).isEqualTo(11);
        assertThat(value(dedicated, "SELECT COUNT(*) FROM tenants WHERE id = 'dedicated-a'"))
                .isZero();
    }

    @Test
    void migratesNewDedicatedDatabaseBeforeRoutingItsFirstConnection() throws Exception {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findById("dedicated-a")).thenReturn(java.util.Optional.of(tenant()));
        TenantDatabaseCredentialsProvider credentials = credentials();
        HikariConfig sharedConfig = new HikariConfig();
        sharedConfig.setJdbcUrl(shared.getJdbcUrl());
        sharedConfig.setUsername(shared.getUsername());
        sharedConfig.setPassword(shared.getPassword());
        try (TenantAwareRoutingDataSource routing = new TenantAwareRoutingDataSource(
                registry, credentials, migrator("classpath:db/migration"), new HikariDataSource(sharedConfig))) {
            TenantContext.setCurrentTenantId("dedicated-a");
            try (Connection connection = routing.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM tenants WHERE id = 'dedicated-a'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            assertThat(routing.dedicatedDataSources()).containsKey("tenant-dedicated-a");
        }
        assertThat(historyCount(dedicated)).isEqualTo(14);
    }

    @Test
    void abortsStartupWhenRegisteredDedicatedDatabaseMigrationFails() throws Exception {
        Flyway sharedFlyway = flyway(shared);
        sharedFlyway.migrate();
        try (Connection connection = connection(shared);
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tenants (id, name, deployment_mode, database_config_id, plan_id, "
                    + "status, created_at, updated_at) VALUES "
                    + "('dedicated-a', 'Dedicated A', 'DEDICATED_DB', 'dedicated-secret', 'pro', "
                    + "'ACTIVE', NOW(), NOW())");
        }
        TenantDatabaseMigrator migrator = migrator(
                "classpath:db/migration", "classpath:db/failing-migration");

        assertThatThrownBy(() -> new DatabaseMigrationConfiguration()
                .migrateSharedAndDedicatedDatabases(migrator)
                .migrate(sharedFlyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant/V13__broken.sql")
                .hasMessageContaining("V13__broken.sql");
    }

    @Test
    void preservesPreviouslyAppliedDevelopmentSeedHistory() {
        Flyway withDevSeed = Flyway.configure()
                .dataSource(shared.getJdbcUrl(), shared.getUsername(), shared.getPassword())
                .locations("classpath:db/migration", "classpath:db/dev-migration")
                .load();
        withDevSeed.migrate();
        assertThat(historyCount(shared)).isEqualTo(16);

        Flyway productionLocations = flyway(shared);
        assertThat(MigrationLocationCompatibility.includePreviouslyAppliedDevSeed(productionLocations)
                .migrate().migrationsExecuted).isZero();
        assertThat(historyCount(shared)).isEqualTo(16);
    }

    private TenantDatabaseMigrator migrator(String... locations) {
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantDatabaseCredentialsProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(credentials());
        return new TenantDatabaseMigrator(provider, locations);
    }

    private PostgreSQLContainer postgres() {
        // The Alpine image can emit the final readiness line without a trailing
        // whitespace character. Its default log-based wait then times out even
        // though PostgreSQL is accepting connections. A port wait verifies the
        // actual capability required by this integration test.
        return new PostgreSQLContainer("postgres:16-alpine")
                .waitingFor(Wait.forListeningPort());
    }

    private TenantDatabaseCredentialsProvider credentials() {
        return new TenantDatabaseCredentialsProvider() {
            @Override
            public DatabaseConnectionDetails resolve(DatabaseConfig config) {
                return new DatabaseConnectionDetails(
                        dedicated.getJdbcUrl(), "dedicated_runtime", "dedicated_runtime");
            }

            @Override
            public DatabaseConnectionDetails resolveForMigration(DatabaseConfig config) {
                return new DatabaseConnectionDetails(
                        dedicated.getJdbcUrl(), dedicated.getUsername(), dedicated.getPassword());
            }
        };
    }

    private Connection runtimeConnection() throws Exception {
        return DriverManager.getConnection(dedicated.getJdbcUrl(), "dedicated_runtime", "dedicated_runtime");
    }

    private TenantConfig tenant() {
        return new TenantConfig("dedicated-a", "Dedicated A", List.of(), DeploymentMode.DEDICATED_DB,
                new DatabaseConfig("dedicated-secret"), "pro", TenantStatus.ACTIVE, Map.of());
    }

    private Flyway flyway(PostgreSQLContainer postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private Connection connection(PostgreSQLContainer postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private int historyCount(PostgreSQLContainer postgres) {
        return value(postgres, "SELECT COUNT(*) FROM flyway_schema_history WHERE success");
    }

    private int value(PostgreSQLContainer postgres, String sql) {
        try (Connection connection = connection(postgres);
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
