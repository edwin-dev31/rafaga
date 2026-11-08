package com.rafaga.backend.tenant.application.port;

import com.rafaga.backend.tenant.domain.model.DatabaseConfig;

/** Resolves a tenant's secret reference into ephemeral connection details. */
public interface TenantDatabaseCredentialsProvider {
    DatabaseConnectionDetails resolve(DatabaseConfig databaseConfig);

    /** Supply a schema owner separately from the restricted runtime role. */
    default DatabaseConnectionDetails resolveForMigration(DatabaseConfig databaseConfig) {
        throw new IllegalStateException("Dedicated database migration credentials are not configured");
    }
}
