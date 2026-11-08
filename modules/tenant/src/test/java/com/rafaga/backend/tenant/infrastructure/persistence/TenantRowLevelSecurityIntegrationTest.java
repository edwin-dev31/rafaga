package com.rafaga.backend.tenant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TenantRowLevelSecurityIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static HikariDataSource dataSource;
    private static HikariDataSource administratorDataSource;
    private final TenantConnectionCustomizer connectionCustomizer = new TenantConnectionCustomizer();

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(POSTGRES.getJdbcUrl());
        configuration.setUsername(POSTGRES.getUsername());
        configuration.setPassword(POSTGRES.getPassword());
        administratorDataSource = new HikariDataSource(configuration);
        try (Connection connection = administratorDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE protected_rows (id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, value VARCHAR(100) NOT NULL)");
            statement.execute("INSERT INTO protected_rows (id, tenant_id, value) VALUES ('a', 'tenant-a', 'visible-to-a'), ('b', 'tenant-b', 'visible-to-b')");
            statement.execute("ALTER TABLE protected_rows ENABLE ROW LEVEL SECURITY");
            statement.execute("ALTER TABLE protected_rows FORCE ROW LEVEL SECURITY");
            statement.execute("CREATE POLICY tenant_isolation ON protected_rows FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')) WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), ''))");
            statement.execute("CREATE ROLE tenant_app LOGIN PASSWORD 'tenant_app'");
            statement.execute("GRANT USAGE ON SCHEMA public TO tenant_app");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON protected_rows TO tenant_app");
        }
        HikariConfig applicationConfiguration = new HikariConfig();
        applicationConfiguration.setJdbcUrl(POSTGRES.getJdbcUrl());
        applicationConfiguration.setUsername("tenant_app");
        applicationConfiguration.setPassword("tenant_app");
        dataSource = new HikariDataSource(applicationConfiguration);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
        administratorDataSource.close();
    }

    @Test
    void returnsNoRowsWhenNoTenantIsSetAndOnlyRowsForTheCurrentTenantOtherwise() throws SQLException {
        assertThat(readValues()).isEmpty();

        TenantContext.setCurrentTenantId("tenant-a");

        assertThat(readValues()).containsExactly("visible-to-a");
    }

    @Test
    void blocksUpdatesToRowsOwnedByAnotherTenant() throws SQLException {
        TenantContext.setCurrentTenantId("tenant-b");

        assertThat(updateValue("a", "attempted-cross-tenant-update")).isZero();

        TenantContext.setCurrentTenantId("tenant-a");
        assertThat(readValues()).containsExactly("visible-to-a");
    }

    private List<String> readValues() throws SQLException {
        try (Connection connection = connectionCustomizer.customize(dataSource.getConnection());
                PreparedStatement statement = connection.prepareStatement("SELECT value FROM protected_rows ORDER BY id");
                ResultSet result = statement.executeQuery()) {
            List<String> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }

    private int updateValue(String id, String value) throws SQLException {
        try (Connection connection = connectionCustomizer.customize(dataSource.getConnection());
                PreparedStatement statement = connection.prepareStatement("UPDATE protected_rows SET value = ? WHERE id = ?")) {
            statement.setString(1, value);
            statement.setString(2, id);
            return statement.executeUpdate();
        }
    }
}
