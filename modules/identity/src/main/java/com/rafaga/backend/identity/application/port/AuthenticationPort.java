package com.rafaga.backend.identity.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    default List<String> getUserRoles(String keycloakId) {
        return List.of();
    }

    default Map<String, List<String>> getUserRolesBatch(Collection<String> keycloakIds) {
        if (keycloakIds == null || keycloakIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (String keycloakId : keycloakIds) {
            result.put(keycloakId, getUserRoles(keycloakId));
        }
        return result;
    }

    default List<String> replaceUserRoles(String keycloakId, List<String> roles) {
        throw new UnsupportedOperationException("Role updates are not implemented");
    }
}
