package com.rafaga.backend.identity.application.port;

import java.time.Instant;

public record TokenValidationResult(boolean valid, String subject, Instant expiresAt) {
    public static TokenValidationResult invalid() {
        return new TokenValidationResult(false, null, null);
    }
}
