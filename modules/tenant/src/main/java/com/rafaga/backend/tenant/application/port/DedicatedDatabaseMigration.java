package com.rafaga.backend.tenant.application.port;

import com.rafaga.backend.tenant.domain.model.TenantConfig;

/** Ensures a dedicated database has the current schema before opening it to application traffic. */
public interface DedicatedDatabaseMigration {
    void migrate(TenantConfig tenant);
}
