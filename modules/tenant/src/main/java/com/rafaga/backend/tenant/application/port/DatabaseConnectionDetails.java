package com.rafaga.backend.tenant.application.port;

import java.util.Objects;

/** Resolved connection details. Instances are supplied by a secrets-backed adapter, never persisted in TenantConfig. */
public record DatabaseConnectionDetails(String jdbcUrl, String username, String password) {
    public DatabaseConnectionDetails {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
