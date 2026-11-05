package com.rafaga.backend.identity.application.port;

import java.util.Objects;

public record IdentityProfile(String keycloakId, String username, String email, String tenantId) {
    public IdentityProfile {
        Objects.requireNonNull(keycloakId, "keycloakId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
    }
}
