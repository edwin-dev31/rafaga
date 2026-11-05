package com.rafaga.backend.identity.application.port;

public interface AuthenticationPort {
    AuthenticationTokens login(String username, String password);

    TokenValidationResult validateToken(String token);

    IdentityProfile getUserInfo(String accessToken);

    IdentityProfile syncUserFromKeycloak(String accessToken);

    IdentityProfile registerUser(String tenantId, UserRegistration registration);
}
