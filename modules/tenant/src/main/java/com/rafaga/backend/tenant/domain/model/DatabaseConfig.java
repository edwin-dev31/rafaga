package com.rafaga.backend.tenant.domain.model;

import java.util.Objects;

/** Reference to externally managed database credentials; never stores credentials. */
public record DatabaseConfig(String secretReference) {
    public DatabaseConfig {
        Objects.requireNonNull(secretReference, "secretReference must not be null");
    }
}
