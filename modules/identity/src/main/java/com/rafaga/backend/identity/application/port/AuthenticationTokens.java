package com.rafaga.backend.identity.application.port;

import java.util.Objects;

public record AuthenticationTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds) {
    public AuthenticationTokens {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(tokenType, "tokenType must not be null");
    }
}
