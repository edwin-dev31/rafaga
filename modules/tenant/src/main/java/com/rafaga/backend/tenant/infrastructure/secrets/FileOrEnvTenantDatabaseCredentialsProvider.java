package com.rafaga.backend.tenant.infrastructure.secrets;

import com.rafaga.backend.tenant.application.port.DatabaseConnectionDetails;
import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Production secrets-backed credentials provider for dedicated tenant databases.
 * Resolves a tenant's secret reference from mounted secret files (default /run/secrets/tenant-db)
 * or environment variables (RAFAGA_TENANT_DB_SECRET_<REF>), enforcing distinct least-privilege
 * runtime and migration roles.
 */
@Component
public class FileOrEnvTenantDatabaseCredentialsProvider implements TenantDatabaseCredentialsProvider {
    private static final Pattern SAFE_SECRET_REF = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_./-]{0,199}$");
    private static final Set<String> DISALLOWED_RUNTIME_ROLES = Set.of("postgres", "root", "admin", "superuser");

    private final Path secretsDirectory;
    private final ObjectMapper objectMapper;
    private final Function<String, String> envLookup;

    @Autowired
    public FileOrEnvTenantDatabaseCredentialsProvider(
            ObjectMapper objectMapper,
            @Value("${rafaga.tenant.database-secrets-dir:/run/secrets/tenant-db}") String secretsDir) {
        this(Path.of(secretsDir), objectMapper, System::getenv);
    }

    FileOrEnvTenantDatabaseCredentialsProvider(
            Path secretsDirectory,
            ObjectMapper objectMapper,
            Function<String, String> envLookup) {
        this.secretsDirectory = secretsDirectory;
        this.objectMapper = objectMapper;
        this.envLookup = envLookup;
    }

    @Override
    public DatabaseConnectionDetails resolve(DatabaseConfig databaseConfig) {
        SecretBundle bundle = loadBundle(databaseConfig);
        return new DatabaseConnectionDetails(bundle.jdbcUrl(), bundle.runtimeUsername(), bundle.runtimePassword());
    }

    @Override
    public DatabaseConnectionDetails resolveForMigration(DatabaseConfig databaseConfig) {
        SecretBundle bundle = loadBundle(databaseConfig);
        return new DatabaseConnectionDetails(bundle.jdbcUrl(), bundle.migrationUsername(), bundle.migrationPassword());
    }

    private SecretBundle loadBundle(DatabaseConfig databaseConfig) {
        if (databaseConfig == null || databaseConfig.secretReference() == null || databaseConfig.secretReference().isBlank()) {
            throw new IllegalArgumentException("databaseConfig secretReference is required");
        }
        String reference = databaseConfig.secretReference().trim();
        if (!SAFE_SECRET_REF.matcher(reference).matches() || reference.contains("..")) {
            throw new IllegalArgumentException("Invalid database secret reference format");
        }

        String rawJson = readSecretPayload(reference);
        JsonNode node;
        try {
            node = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalStateException("Malformed tenant database secret payload for reference: " + reference);
        }

        String jdbcUrl = requiredText(node, "jdbcUrl", reference);
        String runtimeUser = requiredText(node, "runtimeUsername", reference);
        String runtimePassword = requiredText(node, "runtimePassword", reference);
        String migrationUser = requiredText(node, "migrationUsername", reference);
        String migrationPassword = requiredText(node, "migrationPassword", reference);

        if (DISALLOWED_RUNTIME_ROLES.contains(runtimeUser.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Runtime database role must follow least-privilege policy");
        }
        if (runtimeUser.equalsIgnoreCase(migrationUser)) {
            throw new IllegalStateException("Runtime database role must be distinct from migration schema-owner role");
        }
        return new SecretBundle(jdbcUrl, runtimeUser, runtimePassword, migrationUser, migrationPassword);
    }

    private String readSecretPayload(String reference) {
        Path normalizedBase = secretsDirectory.toAbsolutePath().normalize();
        Path filePath = normalizedBase.resolve(reference + ".json").normalize();
        if (filePath.startsWith(normalizedBase) && Files.isRegularFile(filePath)) {
            try {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to read tenant database secret file for reference: " + reference, ex);
            }
        }
        String envKey = "RAFAGA_TENANT_DB_SECRET_"
                + reference.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase(Locale.ROOT);
        String envValue = envLookup.apply(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        throw new IllegalStateException("Tenant database secret not found for reference: " + reference);
    }

    private static String requiredText(JsonNode node, String field, String reference) {
        String value = node.path(field).asString("");
        if (value.isBlank()) {
            throw new IllegalStateException("Missing required field '" + field + "' in tenant database secret: " + reference);
        }
        return value;
    }

    private record SecretBundle(
            String jdbcUrl,
            String runtimeUsername,
            String runtimePassword,
            String migrationUsername,
            String migrationPassword) {
    }
}
