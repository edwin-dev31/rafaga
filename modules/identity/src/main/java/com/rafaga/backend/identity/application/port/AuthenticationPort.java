package com.rafaga.backend.identity.application.port;

public interface AuthenticationPort {
    AuthenticationTokens login(String username, String password);

    default AuthenticationTokens refresh(String refreshToken) {
        throw new UnsupportedOperationException("Refresh is not implemented");
    }

    default void logout(String refreshToken) {
        throw new UnsupportedOperationException("Logout is not implemented");
    }

    TokenValidationResult validateToken(String token);

    IdentityProfile getUserInfo(String accessToken);

    IdentityProfile syncUserFromKeycloak(String accessToken);

    IdentityProfile registerUser(String tenantId, UserRegistration registration);

    default IdentityProfile registerUser(String tenantId, UserRegistration registration, String role) {
        return registerUser(tenantId, registration);
    }

    void updatePassword(String keycloakId, String newPassword, boolean temporary);

    default void setUserEnabled(String keycloakId, boolean enabled) {
        throw new UnsupportedOperationException("User activation toggle is not implemented");
    }
}
