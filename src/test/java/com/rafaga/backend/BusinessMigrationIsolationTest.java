package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class BusinessMigrationIsolationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PRODUCT_A = "00000000-0000-0000-0000-0000000000a1";
    private static final String PRODUCT_B = "00000000-0000-0000-0000-0000000000b1";
    private static final String CATEGORY_A = "00000000-0000-0000-0000-0000000000a2";
    private static final String CATEGORY_B = "00000000-0000-0000-0000-0000000000b2";
    private static final String MEDIA_ASSET_A = "00000000-0000-0000-0000-0000000000f1";

    @BeforeAll
    static void migrateAndSeed() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE catalog_app LOGIN PASSWORD 'catalog_app'");
            statement.execute("GRANT USAGE ON SCHEMA public TO catalog_app");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO catalog_app");
            statement.execute("INSERT INTO tenants (id, name, deployment_mode, plan_id, status, created_at, updated_at) "
                    + "VALUES ('tenant-a', 'A', 'SHARED_DB', 'starter', 'ACTIVE', NOW(), NOW()), "
                    + "('tenant-b', 'B', 'SHARED_DB', 'starter', 'ACTIVE', NOW(), NOW())");
        }

        try (Connection connection = applicationConnection("tenant-a");
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO categories (id, tenant_id, name, slug, display_order) "
                    + "VALUES ('" + CATEGORY_A + "', 'tenant-a', 'Category A', 'category-a', 0)");
            statement.execute("INSERT INTO products (id, tenant_id, sku, name, base_price, status, created_at, updated_at) "
                    + "VALUES ('" + PRODUCT_A + "', 'tenant-a', 'SKU-A', 'Product A', 10, 'ACTIVE', NOW(), NOW())");
        }
        try (Connection connection = applicationConnection("tenant-b");
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO categories (id, tenant_id, name, slug, display_order) "
                    + "VALUES ('" + CATEGORY_B + "', 'tenant-b', 'Category B', 'category-b', 0)");
            statement.execute("INSERT INTO products (id, tenant_id, sku, name, base_price, status, created_at, updated_at) "
                    + "VALUES ('" + PRODUCT_B + "', 'tenant-b', 'SKU-B', 'Product B', 10, 'ACTIVE', NOW(), NOW())");
        }
    }

    @Test
    void filtersCatalogRowsByTenantAndDeniesAccessWithoutTenant() throws SQLException {
        try (Connection missing = applicationConnection("");
                Connection tenantA = applicationConnection("tenant-a");
                Connection tenantB = applicationConnection("tenant-b")) {
            assertThat(names(missing, "products")).isEmpty();
            assertThat(names(tenantA, "products")).containsExactly("Product A");
            assertThat(names(tenantB, "products")).containsExactly("Product B");
            assertThat(names(tenantA, "categories")).containsExactly("Category A");
            assertThat(names(tenantB, "categories")).containsExactly("Category B");
        }
    }

    @Test
    void blocksCrossTenantCategoryAssociationAndVariantOwnership() throws SQLException {
        try (Connection tenantA = applicationConnection("tenant-a");
                Statement statement = tenantA.createStatement()) {
            assertThatThrownBy(() -> statement.execute("INSERT INTO product_categories (product_id, category_id) "
                    + "VALUES ('" + PRODUCT_A + "', '" + CATEGORY_B + "')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("INSERT INTO product_variants "
                    + "(id, tenant_id, product_id, sku, name, price_adjustment, status) VALUES "
                    + "('00000000-0000-0000-0000-0000000000a3', 'tenant-a', '" + PRODUCT_B
                    + "', 'VAR-A', 'Variant A', 0, 'ACTIVE')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void forcesRowLevelSecurityOnCatalogTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT relname FROM pg_class WHERE relname IN "
                        + "('products', 'categories', 'product_variants', 'product_categories', 'variant_attributes') "
                        + "AND relrowsecurity AND relforcerowsecurity ORDER BY relname")) {
            List<String> protectedTables = new ArrayList<>();
            while (result.next()) {
                protectedTables.add(result.getString(1));
            }
            assertThat(protectedTables).containsExactly(
                    "categories", "product_categories", "product_variants", "products", "variant_attributes");
        }
    }

    @Test
    void filtersInventoryAndOrdersAndRejectsCrossTenantWrites() throws SQLException {
        try (Connection tenantA = applicationConnection("tenant-a");
                Statement statement = tenantA.createStatement()) {
            statement.execute("INSERT INTO stock_levels "
                    + "(id, tenant_id, product_id, total_quantity, available_quantity, "
                    + "reserved_quantity, reorder_level, reorder_quantity, last_updated) VALUES "
                    + "('00000000-0000-0000-0000-0000000000a4', 'tenant-a', '" + PRODUCT_A
                    + "', 10, 10, 0, 0, 0, NOW())");
            statement.execute("INSERT INTO orders "
                    + "(id, tenant_id, customer_id, status, subtotal, total, created_at, updated_at) VALUES "
                    + "('00000000-0000-0000-0000-0000000000a5', 'tenant-a', "
                    + "'00000000-0000-0000-0000-0000000000a6', 'PENDING', 10, 10, NOW(), NOW())");
        }
        try (Connection tenantA = applicationConnection("tenant-a");
                Connection tenantB = applicationConnection("tenant-b")) {
            assertThat(rowCount(tenantA, "stock_levels")).isEqualTo(1);
            assertThat(rowCount(tenantA, "orders")).isEqualTo(1);
            assertThat(rowCount(tenantB, "stock_levels")).isZero();
            assertThat(rowCount(tenantB, "orders")).isZero();
            try (Statement statement = tenantB.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE stock_levels SET available_quantity = 9 "
                        + "WHERE tenant_id = 'tenant-a'")).isZero();
                assertThatThrownBy(() -> statement.execute("INSERT INTO orders "
                        + "(id, tenant_id, customer_id, status, subtotal, total, created_at, updated_at) VALUES "
                        + "('00000000-0000-0000-0000-0000000000b5', 'tenant-a', "
                        + "'00000000-0000-0000-0000-0000000000b6', 'PENDING', 10, 10, NOW(), NOW())"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void forcesRowLevelSecurityOnInventoryAndOrderTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT relname FROM pg_class WHERE relname IN "
                        + "('stock_levels', 'inventory_movements', 'stock_reservations', 'orders', 'order_lines') "
                        + "AND relrowsecurity AND relforcerowsecurity ORDER BY relname")) {
            List<String> protectedTables = new ArrayList<>();
            while (result.next()) {
                protectedTables.add(result.getString(1));
            }
            assertThat(protectedTables).containsExactly(
                    "inventory_movements", "order_lines", "orders", "stock_levels", "stock_reservations");
        }
    }

    @Test
    void isolatesIdentityUsers() throws SQLException {
        String userA = "00000000-0000-0000-0000-0000000000c1";
        try (Connection tenantA = applicationConnection("tenant-a");
                Statement statement = tenantA.createStatement()) {
            statement.execute("INSERT INTO users (id, tenant_id, keycloak_id, username, email, status, created_at) "
                    + "VALUES ('" + userA + "', 'tenant-a', 'keycloak-a', 'user-a', 'a@example.test', 'ACTIVE', NOW())");
        }
        try (Connection tenantB = applicationConnection("tenant-b");
                Statement statement = tenantB.createStatement()) {
            assertThat(rowCount(tenantB, "users")).isZero();
        }
        try (Connection tenantA = applicationConnection("tenant-a")) {
            assertThat(rowCount(tenantA, "users")).isEqualTo(1);
        }
    }

    @Test
    void forcesRowLevelSecurityOnIdentityUsers() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT relname FROM pg_class WHERE relname IN "
                        + "('users') "
                        + "AND relrowsecurity AND relforcerowsecurity ORDER BY relname")) {
            List<String> protectedTables = new ArrayList<>();
            while (result.next()) {
                protectedTables.add(result.getString(1));
            }
            assertThat(protectedTables).containsExactly("users");
        }
    }

    @Test
    void filtersCartsAndEnforcesActiveCartAndCrossTenantRules() throws SQLException {
        String cartA = "00000000-0000-0000-0000-0000000000e1";
        String itemA = "00000000-0000-0000-0000-0000000000e2";
        String customerA = "00000000-0000-0000-0000-0000000000e3";
        try (Connection tenantA = applicationConnection("tenant-a");
                Statement statement = tenantA.createStatement()) {
            statement.execute("INSERT INTO carts (id, tenant_id, customer_id, status, created_at, updated_at) "
                    + "VALUES ('" + cartA + "', 'tenant-a', '" + customerA + "', 'ACTIVE', NOW(), NOW())");
            statement.execute("INSERT INTO cart_items (id, tenant_id, cart_id, product_id, variant_id, "
                    + "quantity, unit_price, line_total) VALUES ('" + itemA + "', 'tenant-a', '" + cartA
                    + "', '" + PRODUCT_A + "', NULL, 2, 10.00, 20.00)");
            assertThatThrownBy(() -> statement.execute("INSERT INTO carts "
                    + "(id, tenant_id, customer_id, status, created_at, updated_at) VALUES "
                    + "('00000000-0000-0000-0000-0000000000e4', 'tenant-a', '" + customerA
                    + "', 'ACTIVE', NOW(), NOW())"))
                    .isInstanceOf(SQLException.class);
        }
        try (Connection tenantA = applicationConnection("tenant-a");
                Connection tenantB = applicationConnection("tenant-b")) {
            assertThat(rowCount(tenantA, "carts")).isEqualTo(1);
            assertThat(rowCount(tenantA, "cart_items")).isEqualTo(1);
            assertThat(rowCount(tenantB, "carts")).isZero();
            assertThat(rowCount(tenantB, "cart_items")).isZero();
            try (Statement statement = tenantB.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE carts SET status = 'ABANDONED' "
                        + "WHERE tenant_id = 'tenant-a'")).isZero();
                assertThatThrownBy(() -> statement.execute("INSERT INTO carts "
                        + "(id, tenant_id, customer_id, status, created_at, updated_at) VALUES "
                        + "('00000000-0000-0000-0000-0000000000e5', 'tenant-a', '" + customerA
                        + "', 'ACTIVE', NOW(), NOW())"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void forcesRowLevelSecurityOnCartTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT relname FROM pg_class WHERE relname IN "
                        + "('carts', 'cart_items') AND relrowsecurity AND relforcerowsecurity ORDER BY relname")) {
            List<String> protectedTables = new ArrayList<>();
            while (result.next()) {
                protectedTables.add(result.getString(1));
            }
            assertThat(protectedTables).containsExactly("cart_items", "carts");
        }
    }

    @Test
    void isolatesMediaTablesAndForcesTheirRowLevelSecurity() throws SQLException {
        try (Connection tenantA = applicationConnection("tenant-a");
                Statement statement = tenantA.createStatement()) {
            statement.execute("INSERT INTO media_assets (id, tenant_id, upload_key, upload_expires_at, "
                    + "reserved_bytes, declared_content_type, status, asset_type, created_at, updated_at) VALUES "
                    + "('" + MEDIA_ASSET_A + "', 'tenant-a', 'tenants/tenant-a/uploads/a.jpg', NOW(), "
                    + "0, 'image/jpeg', 'PENDING', 'PRODUCT', NOW(), NOW())");
            statement.execute("INSERT INTO tenant_media_quotas (tenant_id, max_assets, max_storage_bytes, "
                    + "used_assets, used_storage_bytes, reserved_assets, reserved_bytes, updated_at) VALUES "
                    + "('tenant-a', 100, 1000000, 0, 0, 1, 0, NOW())");
        }

        try (Connection tenantA = applicationConnection("tenant-a");
                Connection tenantB = applicationConnection("tenant-b")) {
            assertThat(rowCount(tenantA, "media_assets")).isEqualTo(1);
            assertThat(rowCount(tenantA, "tenant_media_quotas")).isEqualTo(1);
            assertThat(rowCount(tenantB, "media_assets")).isZero();
            assertThat(rowCount(tenantB, "tenant_media_quotas")).isZero();
            try (Statement statement = tenantB.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE media_assets SET status = 'FAILED' "
                        + "WHERE tenant_id = 'tenant-a'")).isZero();
            }
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT relname FROM pg_class WHERE relname IN "
                        + "('media_assets', 'product_images', 'tenant_media_quotas') "
                        + "AND relrowsecurity AND relforcerowsecurity ORDER BY relname")) {
            List<String> protectedTables = new ArrayList<>();
            while (result.next()) {
                protectedTables.add(result.getString(1));
            }
            assertThat(protectedTables).containsExactly("media_assets", "product_images", "tenant_media_quotas");
        }
    }

    private static Connection applicationConnection(String tenantId) throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "catalog_app", "catalog_app");
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
            statement.setString(1, tenantId);
            statement.execute();
        }
        return connection;
    }

    private static List<String> names(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT name FROM " + table + " ORDER BY name")) {
            List<String> values = new ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }

    private static int rowCount(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }
}
