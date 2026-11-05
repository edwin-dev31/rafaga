package com.rafaga.backend.identity.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.TokenValidationResult;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class KeycloakAdapter implements AuthenticationPort {
    private final KeycloakProperties properties;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final RestClient restClient;

    public KeycloakAdapter(KeycloakProperties properties, ObjectProvider<JwtDecoder> jwtDecoderProvider) {
        this.properties = properties;
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.restClient = RestClient.create();
    }

    @Override
    public AuthenticationTokens login(String username, String password) {
        try {
            return requestUserTokens(username, password);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest exception) {
            throw new InvalidCredentialsException();
        }
    }

    @Override
    public TokenValidationResult validateToken(String token) {
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder == null) {
            return TokenValidationResult.invalid();
        }
        try {
            Jwt jwt = decoder.decode(token);
            return new TokenValidationResult(true, jwt.getSubject(), jwt.getExpiresAt());
        } catch (JwtException exception) {
            return TokenValidationResult.invalid();
        }
    }

    @Override
    public IdentityProfile getUserInfo(String accessToken) {
        KeycloakUserInfo response = restClient.get()
                .uri(userInfoUri())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(KeycloakUserInfo.class);
        return Objects.requireNonNull(response, "Keycloak returned an empty user-info response").toProfile();
    }

    @Override
    public IdentityProfile syncUserFromKeycloak(String accessToken) {
        return getUserInfo(accessToken);
    }

    @Override
    public IdentityProfile registerUser(String tenantId, UserRegistration registration) {
        String adminAccessToken = requestAdminAccessToken();
        KeycloakUserRegistration payload = new KeycloakUserRegistration(
                registration.username(),
                registration.firstName(),
                registration.lastName(),
                registration.email(),
                tenantId,
                registration.password());
        ResponseEntity<Void> response;
        try {
            response = restClient.post()
                    .uri(adminUsersUri())
                    .headers(headers -> headers.setBearerAuth(adminAccessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict exception) {
            throw new UserAlreadyExistsException();
        }
        String keycloakId = identifierFrom(response.getHeaders().getLocation());
        assignStoreOwnerRole(adminAccessToken, keycloakId);
        return new IdentityProfile(keycloakId, registration.username(), registration.email(), tenantId);
    }

    private AuthenticationTokens requestUserTokens(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", required(properties.getClientId(), "clientId"));
        form.add("client_secret", required(properties.getClientSecret(), "clientSecret"));
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        return requestTokens(form);
    }

    private String requestAdminAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", required(properties.getAdminClientId(), "adminClientId"));
        form.add("client_secret", required(properties.getAdminClientSecret(), "adminClientSecret"));
        return requestTokens(form).accessToken();
    }

    private AuthenticationTokens requestTokens(MultiValueMap<String, String> form) {
        TokenResponse response = restClient.post()
                .uri(tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        return Objects.requireNonNull(response, "Keycloak returned an empty token response").toTokens();
    }

    private String tokenUri() {
        return issuerUri() + "/protocol/openid-connect/token";
    }

    private String userInfoUri() {
        return issuerUri() + "/protocol/openid-connect/userinfo";
    }

    private String adminUsersUri() {
        return issuerUri().replace("/realms/", "/admin/realms/") + "/users";
    }

    private String adminRoleUri(String keycloakId) {
        return adminUsersUri() + "/" + keycloakId + "/role-mappings/realm";
    }

    private String realmRoleUri() {
        return issuerUri().replace("/realms/", "/admin/realms/") + "/roles/STORE_OWNER";
    }

    private void assignStoreOwnerRole(String adminAccessToken, String keycloakId) {
        KeycloakRole role = restClient.get()
                .uri(realmRoleUri())
                .headers(headers -> headers.setBearerAuth(adminAccessToken))
                .retrieve()
                .body(KeycloakRole.class);
        restClient.post()
                .uri(adminRoleUri(keycloakId))
                .headers(headers -> headers.setBearerAuth(adminAccessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Objects.requireNonNull(role, "Keycloak returned an empty role response")))
                .retrieve()
                .toBodilessEntity();
    }

    private String identifierFrom(URI location) {
        if (location == null || location.getPath() == null) {
            throw new IllegalStateException("Keycloak did not return the created user location");
        }
        int separator = location.getPath().lastIndexOf('/');
        if (separator < 0 || separator == location.getPath().length() - 1) {
            throw new IllegalStateException("Keycloak returned an invalid created user location");
        }
        return location.getPath().substring(separator + 1);
    }

    private String issuerUri() {
        return required(properties.getIssuerUri(), "issuerUri").replaceAll("/+$", "");
    }

    private String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Keycloak " + propertyName + " must be configured");
        }
        return value;
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
        private AuthenticationTokens toTokens() {
            return new AuthenticationTokens(accessToken, refreshToken, tokenType, expiresIn);
        }
    }

    private record KeycloakUserRegistration(
            String username,
            String firstName,
            String lastName,
            String email,
            Map<String, List<String>> attributes,
            boolean enabled,
            boolean emailVerified,
            List<KeycloakCredential> credentials) {
        private KeycloakUserRegistration(
                String username,
                String firstName,
                String lastName,
                String email,
                String tenantId,
                String password) {
            this(
                    username,
                    firstName,
                    lastName,
                    email,
                    Map.of("tenant_id", List.of(tenantId)),
                    true,
                    true,
                    List.of(new KeycloakCredential(password)));
        }
    }

    private record KeycloakCredential(String type, String value, boolean temporary) {
        private KeycloakCredential(String password) {
            this("password", password, false);
        }
    }

    private record KeycloakRole(String id, String name) {
    }

    private record KeycloakUserInfo(
            @JsonProperty("sub") String keycloakId,
            @JsonProperty("preferred_username") String username,
            String email,
            @JsonProperty("tenant_id") String tenantId) {
        private IdentityProfile toProfile() {
            return new IdentityProfile(keycloakId, username, email, tenantId);
        }
    }
}
