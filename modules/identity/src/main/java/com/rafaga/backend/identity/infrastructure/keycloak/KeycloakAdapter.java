package com.rafaga.backend.identity.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.TokenValidationResult;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class KeycloakAdapter implements AuthenticationPort {
    private static final Logger log = LoggerFactory.getLogger(KeycloakAdapter.class);
    private static final Set<String> MANAGED_ROLES = Set.of("SUPER_ADMIN", "STORE_MANAGER", "CUSTOMER");
    private final KeycloakProperties properties;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final RestClient restClient;

    public KeycloakAdapter(KeycloakProperties properties, ObjectProvider<JwtDecoder> jwtDecoderProvider,
            @Qualifier("keycloakRestClient") RestClient restClient) {
        this.properties = properties;
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.restClient = restClient;
    }

    @Override
    public AuthenticationTokens login(String username, String password) {
        try {
            return requestUserTokens(username, password);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest exception) {
            throw new InvalidCredentialsException();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new IdentityProviderUnavailableException("Authentication service is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
        } catch (RuntimeException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("must be configured")) {
                throw exception;
            }
            throw new IdentityProviderUnavailableException("Authentication service is temporarily unavailable", exception);
        }
    }

    @Override
    public AuthenticationTokens refresh(String refreshToken) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("client_id", required(properties.getClientId(), "clientId"));
            form.add("client_secret", required(properties.getClientSecret(), "clientSecret"));
            form.add("refresh_token", refreshToken);
            return requestTokens(form);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest exception) {
            throw new InvalidCredentialsException();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new IdentityProviderUnavailableException("Authentication service is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
        }
    }

    @Override
    public void logout(String refreshToken) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", required(properties.getClientId(), "clientId"));
            form.add("client_secret", required(properties.getClientSecret(), "clientSecret"));
            form.add("refresh_token", refreshToken);
            restClient.post()
                    .uri(logoutUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest exception) {
            throw new InvalidCredentialsException();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new IdentityProviderUnavailableException("Authentication service is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
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
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder == null) {
            throw new IllegalStateException("JwtDecoder is not configured; cannot decode access token");
        }
        Jwt jwt = decoder.decode(accessToken);
        return new IdentityProfile(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("tenant_id"));
    }

    @Override
    public IdentityProfile syncUserFromKeycloak(String accessToken) {
        return getUserInfo(accessToken);
    }

    @Override
    public IdentityProfile registerUser(String tenantId, UserRegistration registration) {
        return registerUser(tenantId, registration, "STORE_MANAGER");
    }

    @Override
    public IdentityProfile registerUser(String tenantId, UserRegistration registration, String role) {
        String adminAccessToken;
        try {
            adminAccessToken = requestAdminAccessToken();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.provision outcome=IDP_UNAVAILABLE tenantId={} reason=admin_token_fetch_failed",
                    tenantId);
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
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
            log.warn("event=keycloak.provision outcome=CONFLICT tenantId={}", tenantId);
            throw new UserAlreadyExistsException();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.provision outcome=IDP_UNAVAILABLE tenantId={} reason=user_create_failed",
                    tenantId);
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
        String keycloakId = identifierFrom(response.getHeaders().getLocation());
        try {
            assignRealmRole(adminAccessToken, keycloakId, role);
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.provision outcome=ROLE_ASSIGNMENT_FAILED tenantId={}", tenantId);
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
        log.info("event=keycloak.provision outcome=SUCCESS tenantId={} role={}", tenantId, role);
        return new IdentityProfile(keycloakId, registration.username(), registration.email(), tenantId);
    }

    @Override
    public void setUserEnabled(String keycloakId, boolean enabled) {
        String adminAccessToken;
        try {
            adminAccessToken = requestAdminAccessToken();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.user_status outcome=IDP_UNAVAILABLE reason=admin_token_fetch_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
        try {
            restClient.put()
                    .uri(adminUsersUri() + "/" + keycloakId)
                    .headers(headers -> headers.setBearerAuth(adminAccessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", enabled))
                    .retrieve()
                    .toBodilessEntity();
            if (!enabled) {
                restClient.post()
                        .uri(adminUsersUri() + "/" + keycloakId + "/logout")
                        .headers(headers -> headers.setBearerAuth(adminAccessToken))
                        .retrieve()
                        .toBodilessEntity();
            }
            log.info("event=keycloak.user_status outcome=SUCCESS enabled={}", enabled);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IdentityResourceNotFoundException("User", keycloakId);
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.user_status outcome=IDP_UNAVAILABLE enabled={}", enabled);
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
        }
    }

    @Override
    public void updatePassword(String keycloakId, String newPassword, boolean temporary) {
        String adminAccessToken;
        try {
            adminAccessToken = requestAdminAccessToken();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.password_update outcome=IDP_UNAVAILABLE reason=admin_token_fetch_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
        try {
            restClient.put()
                    .uri(adminResetPasswordUri(keycloakId))
                    .headers(headers -> headers.setBearerAuth(adminAccessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new KeycloakCredential("password", newPassword, temporary))
                    .retrieve()
                    .toBodilessEntity();
            log.info("event=keycloak.password_update outcome=SUCCESS");
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IdentityResourceNotFoundException("User", keycloakId);
        } catch (HttpClientErrorException.BadRequest exception) {
            log.warn("event=keycloak.password_update outcome=POLICY_VIOLATION");
            throw new PasswordPolicyViolationException("Password does not meet security policy requirements");
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.password_update outcome=IDP_UNAVAILABLE reason=reset_password_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
        }
    }

    @Override
    public List<String> getUserRoles(String keycloakId) {
        return getUserRolesBatch(List.of(keycloakId)).getOrDefault(keycloakId, List.of());
    }

    @Override
    public Map<String, List<String>> getUserRolesBatch(Collection<String> keycloakIds) {
        if (keycloakIds == null || keycloakIds.isEmpty()) {
            return Map.of();
        }
        String adminAccessToken;
        try {
            adminAccessToken = requestAdminAccessToken();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.roles_lookup outcome=IDP_UNAVAILABLE reason=admin_token_fetch_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
        Map<String, List<String>> rolesByUser = new LinkedHashMap<>();
        for (String keycloakId : keycloakIds) {
            if (keycloakId == null || keycloakId.isBlank() || rolesByUser.containsKey(keycloakId)) {
                continue;
            }
            rolesByUser.put(keycloakId, fetchManagedRolesOrEmpty(adminAccessToken, keycloakId));
        }
        return rolesByUser;
    }

    @Override
    public List<String> replaceUserRoles(String keycloakId, List<String> targetRoles) {
        String adminAccessToken;
        try {
            adminAccessToken = requestAdminAccessToken();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.roles_update outcome=IDP_UNAVAILABLE reason=admin_token_fetch_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        }
        try {
            List<KeycloakRole> currentManaged = fetchAssignedManagedRoleObjects(adminAccessToken, keycloakId);
            Set<String> targetSet = Set.copyOf(targetRoles);
            Set<String> currentNames = currentManaged.stream().map(KeycloakRole::name).collect(java.util.stream.Collectors.toSet());

            List<KeycloakRole> toRemove = currentManaged.stream()
                    .filter(role -> !targetSet.contains(role.name()))
                    .toList();
            if (!toRemove.isEmpty()) {
                restClient.method(HttpMethod.DELETE)
                        .uri(adminRoleUri(keycloakId))
                        .headers(headers -> headers.setBearerAuth(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(toRemove)
                        .retrieve()
                        .toBodilessEntity();
            }

            List<KeycloakRole> toAdd = targetRoles.stream()
                    .filter(roleName -> !currentNames.contains(roleName))
                    .map(roleName -> fetchRealmRole(adminAccessToken, roleName))
                    .toList();
            if (!toAdd.isEmpty()) {
                restClient.post()
                        .uri(adminRoleUri(keycloakId))
                        .headers(headers -> headers.setBearerAuth(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(toAdd)
                        .retrieve()
                        .toBodilessEntity();
            }
            log.info("event=keycloak.roles_update outcome=SUCCESS roles={}", targetRoles);
            return List.copyOf(targetRoles);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IdentityResourceNotFoundException("User", keycloakId);
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.roles_update outcome=IDP_UNAVAILABLE reason=role_update_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
        }
    }

    private List<String> fetchManagedRolesOrEmpty(String adminAccessToken, String keycloakId) {
        try {
            return fetchAssignedManagedRoleObjects(adminAccessToken, keycloakId).stream()
                    .map(KeycloakRole::name)
                    .sorted()
                    .toList();
        } catch (HttpClientErrorException.NotFound exception) {
            return List.of();
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("event=keycloak.roles_lookup outcome=IDP_UNAVAILABLE reason=role_fetch_failed");
            throw new IdentityProviderUnavailableException("Identity provider is temporarily unavailable", exception);
        } catch (HttpClientErrorException exception) {
            throw new IllegalStateException("Keycloak returned an error: " + exception.getStatusCode(), exception);
        }
    }

    private List<KeycloakRole> fetchAssignedManagedRoleObjects(String adminAccessToken, String keycloakId) {
        KeycloakRole[] assigned = restClient.get()
                .uri(adminRoleUri(keycloakId))
                .headers(headers -> headers.setBearerAuth(adminAccessToken))
                .retrieve()
                .body(KeycloakRole[].class);
        if (assigned == null || assigned.length == 0) {
            return List.of();
        }
        return Arrays.stream(assigned)
                .filter(role -> role != null && role.name() != null && MANAGED_ROLES.contains(role.name()))
                .toList();
    }

    private KeycloakRole fetchRealmRole(String adminAccessToken, String roleName) {
        return Objects.requireNonNull(
                restClient.get()
                        .uri(realmRoleUri(roleName))
                        .headers(headers -> headers.setBearerAuth(adminAccessToken))
                        .retrieve()
                        .body(KeycloakRole.class),
                "Keycloak returned an empty role response");
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

    private String logoutUri() {
        return issuerUri() + "/protocol/openid-connect/logout";
    }

    private String userInfoUri() {
        return issuerUri() + "/protocol/openid-connect/userinfo";
    }

    private String adminUsersUri() {
        return issuerUri().replace("/realms/", "/admin/realms/") + "/users";
    }

    private String adminResetPasswordUri(String keycloakId) {
        return adminUsersUri() + "/" + keycloakId + "/reset-password";
    }

    private String adminRoleUri(String keycloakId) {
        return adminUsersUri() + "/" + keycloakId + "/role-mappings/realm";
    }

    private String realmRoleUri(String role) {
        return issuerUri().replace("/realms/", "/admin/realms/") + "/roles/" + role;
    }

    private void assignRealmRole(String adminAccessToken, String keycloakId, String roleName) {
        KeycloakRole role = restClient.get()
                .uri(realmRoleUri(roleName))
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
