package com.rafaga.backend.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.security.RegistrationProperties;
import com.rafaga.backend.identity.application.security.RegistrationTokenValidator;
import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Test
    void changesCurrentUserPasswordAfterVerifyingCurrentCredentials() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        when(authentication.login("person", "CurrentPass123!"))
                .thenReturn(new AuthenticationTokens("access", "refresh", "Bearer", 300));
        authenticateJwt("keycloak-1", "person", TENANT_ID);
        UserService service = service(authentication, mock(IdentityStore.class));

        service.changeCurrentUserPassword("CurrentPass123!", "NewStrongPass456!");

        verify(authentication).login("person", "CurrentPass123!");
        verify(authentication).updatePassword("keycloak-1", "NewStrongPass456!", false);
    }

    @Test
    void rejectsCurrentUserPasswordChangeWhenCurrentPasswordIsInvalid() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        when(authentication.login("person", "WrongPass123!")).thenThrow(new InvalidCredentialsException());
        authenticateJwt("keycloak-1", "person", TENANT_ID);
        UserService service = service(authentication, mock(IdentityStore.class));

        assertThatThrownBy(() -> service.changeCurrentUserPassword("WrongPass123!", "NewStrongPass456!"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(authentication, never()).updatePassword(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void rejectsCurrentUserPasswordChangeWhenJwtBelongsToAnotherTenant() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        authenticateJwt("keycloak-1", "person", "tenant-b");
        UserService service = service(authentication, mock(IdentityStore.class));

        assertThatThrownBy(() -> service.changeCurrentUserPassword("CurrentPass123!", "NewStrongPass456!"))
                .isInstanceOf(CrossTenantAccessException.class);
        verify(authentication, never()).login(any(), any());
        verify(authentication, never()).updatePassword(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void rejectsCurrentUserPasswordChangeWhenNewPasswordMatchesCurrentPassword() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        authenticateJwt("keycloak-1", "person", TENANT_ID);
        UserService service = service(authentication, mock(IdentityStore.class));

        assertThatThrownBy(() -> service.changeCurrentUserPassword("SamePass123!", "SamePass123!"))
                .isInstanceOf(PasswordPolicyViolationException.class);
        verify(authentication, never()).login(any(), any());
        verify(authentication, never()).updatePassword(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void resetsUserPasswordForUserWithinCurrentTenant() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        UUID userId = UUID.randomUUID();
        User user = new User(TENANT_ID, "keycloak-99", "manager", "manager@example.test", Instant.now());
        when(store.findUserById(TENANT_ID, userId)).thenReturn(Optional.of(user));
        UserService service = service(authentication, store);

        service.resetUserPassword(userId, "AdminResetPass123!", true);

        verify(authentication).updatePassword("keycloak-99", "AdminResetPass123!", true);
    }

    @Test
    void rejectsAdminPasswordResetWhenUserDoesNotBelongToCurrentTenant() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        IdentityStore store = mock(IdentityStore.class);
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        UUID otherTenantUserId = UUID.randomUUID();
        when(store.findUserById(TENANT_ID, otherTenantUserId)).thenReturn(Optional.empty());
        UserService service = service(authentication, store);

        assertThatThrownBy(() -> service.resetUserPassword(otherTenantUserId, "AdminResetPass123!", false))
                .isInstanceOf(IdentityResourceNotFoundException.class);
        verify(authentication, never()).updatePassword(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void authenticatesFromDashboardWithoutHostTenantByResolvingJwtTenantClaim() {
        TenantContext.clear();
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        IdentityStore store = mock(IdentityStore.class);
        TenantRegistry registry = mock(TenantRegistry.class);
        when(authentication.login("manager", "Pass123!"))
                .thenReturn(new AuthenticationTokens("access-1", "refresh-1", "Bearer", 300));
        when(authentication.getUserInfo("access-1"))
                .thenReturn(new IdentityProfile("kc-1", "manager", "manager@tienda.test", TENANT_ID));
        when(registry.findById(TENANT_ID)).thenReturn(Optional.of(new TenantConfig(
                TENANT_ID, "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "starter", TenantStatus.ACTIVE, Map.of())));
        when(store.findUserByKeycloakId(TENANT_ID, "kc-1")).thenReturn(Optional.empty());
        when(store.saveUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrationProperties props = new RegistrationProperties();
        props.setRegistrationToken("registration-token");
        UserService service = new UserService(
                authentication, store, new RegistrationTokenValidator(props), null, registry, null, Clock.systemUTC());

        AuthenticationTokens tokens = service.authenticateUser("manager", "Pass123!");

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(TenantContext.isSet()).isFalse();
        verify(store).saveUser(any(User.class));
    }

    @Test
    void togglesTenantUserStatusInKeycloakAndLocalStore() {
        AuthenticationPort authentication = mock(AuthenticationPort.class);
        IdentityStore store = mock(IdentityStore.class);
        UUID userId = UUID.randomUUID();
        User user = new User(TENANT_ID, "kc-55", "manager", "manager@tienda.test", Instant.now());
        when(store.findUserById(TENANT_ID, userId)).thenReturn(Optional.of(user));
        when(store.saveUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UserService service = service(authentication, store);

        UserProfile disabled = service.setTenantUserStatus(TENANT_ID, userId, UserStatus.DISABLED);

        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        verify(authentication).setUserEnabled("kc-55", false);
    }

    private void authenticateJwt(String subject, String username, String tenantId) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", username,
                        "email", username + "@example.test",
                        "tenant_id", tenantId));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private UserService service(AuthenticationPort authentication, IdentityStore store) {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setRegistrationToken("registration-token");
        return new UserService(authentication, store, new RegistrationTokenValidator(properties));
    }
}
