package com.rafaga.backend.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.security.RegistrationProperties;
import com.rafaga.backend.identity.application.security.RegistrationTokenValidator;
import com.rafaga.backend.identity.domain.model.Permission;
import com.rafaga.backend.identity.domain.model.Role;
import com.rafaga.backend.identity.domain.model.User;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserServiceTest {
    private static final String TENANT_ID = "tenant-a";

    @AfterEach
    void clearSecurityContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void checksPermissionsUsingOnlyTheCurrentTenantsUser() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        UUID userId = UUID.randomUUID();
        Role role = new Role(TENANT_ID, "STORE_OWNER", "Store owner", EnumSet.of(Permission.PRODUCT_UPDATE));
        User user = mock(User.class);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(store.findUserById(TENANT_ID, userId)).thenReturn(Optional.of(user));
        UserService service = service(authentication, store);

        assertThat(service.hasPermission(userId, Permission.PRODUCT_UPDATE)).isTrue();
        assertThat(service.hasPermission(userId, Permission.USER_MANAGE)).isFalse();
    }

    @Test
    void returnsNotFoundWhenAUserDoesNotBelongToTheCurrentTenant() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        UUID otherTenantUserId = UUID.randomUUID();
        when(store.findUserById(TENANT_ID, otherTenantUserId)).thenReturn(Optional.empty());
        UserService service = service(mock(AuthenticationPort.class), store);

        assertThatThrownBy(() -> service.getUserById(otherTenantUserId))
                .isInstanceOf(IdentityResourceNotFoundException.class);
    }

    @Test
    void rejectsAKeycloakProfileFromAnotherTenant() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        AuthenticationTokens tokens = new AuthenticationTokens("access", "refresh", "Bearer", 300);
        when(authentication.login("person@example.test", "secret")).thenReturn(tokens);
        when(authentication.getUserInfo("access"))
                .thenReturn(new IdentityProfile("keycloak-1", "person", "person@example.test", "tenant-b"));
        UserService service = service(authentication, mock(IdentityStore.class));

        assertThatThrownBy(() -> service.authenticateUser("person@example.test", "secret"))
                .isInstanceOf(CrossTenantAccessException.class);
    }

    @Test
    void assignsAWithinTenantRoleToTheUser() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        User user = new User(TENANT_ID, "keycloak-1", "person", "person@example.test", Instant.now());
        Role role = new Role(TENANT_ID, "STORE_MANAGER", "Store manager", EnumSet.of(Permission.USER_MANAGE));
        when(store.findUserById(TENANT_ID, userId)).thenReturn(Optional.of(user));
        when(store.findRoleById(TENANT_ID, roleId)).thenReturn(Optional.of(role));
        UserService service = service(mock(AuthenticationPort.class), store);

        service.assignRoleToUser(userId, roleId);

        assertThat(user.getRoles()).contains(role);
        verify(store).saveUser(user);
    }

    @Test
    void synchronizesTheAuthenticatedKeycloakUserOnFirstAccess() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "keycloak-1",
                        "preferred_username", "person",
                        "email", "person@example.test",
                        "tenant_id", TENANT_ID));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(store.findUserByKeycloakId(TENANT_ID, "keycloak-1")).thenReturn(Optional.empty());
        when(store.saveUser(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserService service = service(mock(AuthenticationPort.class), store);

        UserProfile profile = service.getCurrentUser();

        assertThat(profile.username()).isEqualTo("person");
        verify(store).saveUser(any(User.class));
    }

    @Test
    void registersOnlyAfterTheGlobalRegistrationTokenIsValidated() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        UserRegistration registration = new UserRegistration(
                "person", "Person", "Example", "person@example.test", "secret");
        when(authentication.registerUser(TENANT_ID, registration))
                .thenReturn(new IdentityProfile("keycloak-1", "person", "person@example.test", TENANT_ID));
        when(store.findUserByKeycloakId(TENANT_ID, "keycloak-1")).thenReturn(Optional.empty());
        when(store.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserService service = service(authentication, store);

        UserProfile profile = service.registerUser("registration-token", registration);

        assertThat(profile.username()).isEqualTo("person");
        verify(authentication).registerUser(TENANT_ID, registration);
    }

    @Test
    void rejectsRegistrationWithAnInvalidTokenBeforeCallingKeycloak() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        UserService service = service(authentication, mock(IdentityStore.class));

        assertThatThrownBy(() -> service.registerUser(
                "invalid-token",
                new UserRegistration("person", "Person", "Example", "person@example.test", "secret")))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }

    private UserService service(AuthenticationPort authentication, IdentityStore store) {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setRegistrationToken("registration-token");
        return new UserService(authentication, store, new RegistrationTokenValidator(properties));
    }
}
