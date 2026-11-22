package com.rafaga.backend.tenant.infrastructure.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rafaga.backend.tenant.application.port.DatabaseConnectionDetails;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FileOrEnvTenantDatabaseCredentialsProviderTest {
    private static final String VALID_JSON = """
            {
              "jdbcUrl": "jdbc:postgresql://db-vip.internal:5432/tenant_vip?sslmode=require",
              "runtimeUsername": "tenant_vip_runtime",
              "runtimePassword": "runtime-secret-password",
              "migrationUsername": "tenant_vip_owner",
              "migrationPassword": "owner-secret-password"
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void resolvesRuntimeAndMigrationCredentialsFromSecretFile() throws Exception {
        Path secretFile = tempDir.resolve("vip-store.json");
        Files.writeString(secretFile, VALID_JSON);

        FileOrEnvTenantDatabaseCredentialsProvider provider =
                new FileOrEnvTenantDatabaseCredentialsProvider(tempDir, new ObjectMapper(), key -> null);

        DatabaseConnectionDetails runtime = provider.resolve(new DatabaseConfig("vip-store"));
        DatabaseConnectionDetails migration = provider.resolveForMigration(new DatabaseConfig("vip-store"));

        assertThat(runtime.jdbcUrl()).isEqualTo("jdbc:postgresql://db-vip.internal:5432/tenant_vip?sslmode=require");
        assertThat(runtime.username()).isEqualTo("tenant_vip_runtime");
        assertThat(migration.username()).isEqualTo("tenant_vip_owner");
        assertThat(runtime.username()).isNotEqualTo(migration.username());
    }

    @Test
    void resolvesCredentialsFromEnvironmentFallback() {
        Map<String, String> env = Map.of("RAFAGA_TENANT_DB_SECRET_DEDICATED_VIP", VALID_JSON);
        FileOrEnvTenantDatabaseCredentialsProvider provider =
                new FileOrEnvTenantDatabaseCredentialsProvider(tempDir, new ObjectMapper(), env::get);

        DatabaseConnectionDetails runtime = provider.resolve(new DatabaseConfig("dedicated/vip"));
        assertThat(runtime.username()).isEqualTo("tenant_vip_runtime");
    }

    @Test
    void rejectsPathTraversalSuperuserRuntimeRoleAndSharedMigrationRole() throws Exception {
        FileOrEnvTenantDatabaseCredentialsProvider provider =
                new FileOrEnvTenantDatabaseCredentialsProvider(tempDir, new ObjectMapper(), key -> null);

        assertThatThrownBy(() -> provider.resolve(new DatabaseConfig("../etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");

        Files.writeString(tempDir.resolve("superuser.json"), """
                {
                  "jdbcUrl": "jdbc:postgresql://db:5432/t1",
                  "runtimeUsername": "postgres",
                  "runtimePassword": "pw",
                  "migrationUsername": "owner",
                  "migrationPassword": "pw2"
                }
                """);
        assertThatThrownBy(() -> provider.resolve(new DatabaseConfig("superuser")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("least-privilege");

        Files.writeString(tempDir.resolve("same-role.json"), """
                {
                  "jdbcUrl": "jdbc:postgresql://db:5432/t1",
                  "runtimeUsername": "tenant_app",
                  "runtimePassword": "pw",
                  "migrationUsername": "tenant_app",
                  "migrationPassword": "pw"
                }
                """);
        assertThatThrownBy(() -> provider.resolve(new DatabaseConfig("same-role")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("distinct");
    }
}
