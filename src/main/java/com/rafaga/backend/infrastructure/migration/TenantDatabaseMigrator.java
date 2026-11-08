package com.rafaga.backend.infrastructure.migration;

import com.rafaga.backend.tenant.application.port.DatabaseConnectionDetails;
import com.rafaga.backend.tenant.application.port.DedicatedDatabaseMigration;
import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantDatabaseMigrator implements DedicatedDatabaseMigration {
    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private final ObjectProvider<TenantDatabaseCredentialsProvider> credentialsProvider;
    private final String[] migrationLocations;

    @Autowired
    public TenantDatabaseMigrator(ObjectProvider<TenantDatabaseCredentialsProvider> credentialsProvider) {
        this(credentialsProvider, MIGRATION_LOCATION);
    }

    TenantDatabaseMigrator(
            ObjectProvider<TenantDatabaseCredentialsProvider> credentialsProvider, String... migrationLocations) {
        this.credentialsProvider = credentialsProvider;
        this.migrationLocations = Arrays.copyOf(migrationLocations, migrationLocations.length);
    }

    public void migrateRegistered(DataSource sharedDatabase) {
        List<TenantConfig> tenants = new ArrayList<>();
        try (Connection connection = sharedDatabase.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, name, database_config_id, plan_id, status "
                        + "FROM tenants WHERE deployment_mode = 'DEDICATED_DB'")) {
            while (rows.next()) {
                String secretReference = rows.getString("database_config_id");
                tenants.add(new TenantConfig(
                        rows.getString("id"), rows.getString("name"), List.of(), DeploymentMode.DEDICATED_DB,
                        secretReference == null ? null : new DatabaseConfig(secretReference),
                        rows.getString("plan_id"), TenantStatus.valueOf(rows.getString("status")), Map.of()));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot list dedicated databases after shared migration", exception);
        }
        tenants.forEach(this::migrate);
    }

    @Override
    public void migrate(TenantConfig tenant) {
        if (tenant.deploymentMode() != DeploymentMode.DEDICATED_DB) {
            throw new IllegalArgumentException("Tenant is not configured for a dedicated database: " + tenant.tenantId());
        }
        if (tenant.databaseConfig() == null) {
            throw new IllegalStateException("Dedicated tenant has no database secret reference: " + tenant.tenantId());
        }
        TenantDatabaseCredentialsProvider provider = credentialsProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("Dedicated database credentials provider is not configured for tenant "
                    + tenant.tenantId());
        }
        DatabaseConnectionDetails details = provider.resolveForMigration(tenant.databaseConfig());
        DatabaseConnectionDetails runtime = provider.resolve(tenant.databaseConfig());
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(details.jdbcUrl(), details.username(), details.password())
                    .locations(migrationLocations)
                    .load();
            MigrationLocationCompatibility.includePreviouslyAppliedDevSeed(flyway).migrate();
        } catch (FlywayException exception) {
            throw new IllegalStateException("Dedicated database migration failed for tenant " + tenant.tenantId()
                    + "; module/version: " + exception.getMessage(), exception);
        }
        try {
            ensureTenantRow(details, tenant);
            grantRuntimeAccess(details, runtime);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot prepare dedicated database for runtime access for "
                    + tenant.tenantId(), exception);
        }
    }

    private void grantRuntimeAccess(DatabaseConnectionDetails migration, DatabaseConnectionDetails runtime)
            throws SQLException {
        String runtimeRole = quotedIdentifier(runtime.username());
        try (Connection connection = DriverManager.getConnection(
                migration.jdbcUrl(), migration.username(), migration.password());
                Statement statement = connection.createStatement()) {
            statement.execute("GRANT USAGE ON SCHEMA public TO " + runtimeRole);
            List<String> names = new ArrayList<>();
            try (ResultSet tables = statement.executeQuery("SELECT tablename FROM pg_tables "
                    + "WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'")) {
                while (tables.next()) {
                    names.add(tables.getString(1));
                }
            }
            for (String name : names) {
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public."
                        + quotedIdentifier(name) + " TO " + runtimeRole);
            }
            statement.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO " + runtimeRole);
            statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT "
                    + "SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + runtimeRole);
            statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT "
                    + "USAGE, SELECT ON SEQUENCES TO " + runtimeRole);
        }
    }

    private String quotedIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private void ensureTenantRow(DatabaseConnectionDetails details, TenantConfig tenant) throws SQLException {
        String sql = "INSERT INTO tenants (id, name, deployment_mode, database_config_id, plan_id, status, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
                + "name = EXCLUDED.name, deployment_mode = EXCLUDED.deployment_mode, "
                + "database_config_id = EXCLUDED.database_config_id, plan_id = EXCLUDED.plan_id, "
                + "status = EXCLUDED.status, updated_at = EXCLUDED.updated_at";
        try (Connection connection = DriverManager.getConnection(
                details.jdbcUrl(), details.username(), details.password());
                PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setString(1, tenant.tenantId());
            statement.setString(2, tenant.name());
            statement.setString(3, tenant.deploymentMode().name());
            statement.setString(4, tenant.databaseConfig().secretReference());
            statement.setString(5, tenant.planId());
            statement.setString(6, tenant.status().name());
            statement.setTimestamp(7, now);
            statement.setTimestamp(8, now);
            statement.executeUpdate();
        }
    }
}
