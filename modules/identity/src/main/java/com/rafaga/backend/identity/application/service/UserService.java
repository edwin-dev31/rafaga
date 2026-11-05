package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.UnauthenticatedUserException;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.security.RegistrationTokenValidator;
import com.rafaga.backend.identity.domain.model.Permission;
import com.rafaga.backend.identity.domain.model.Role;
import com.rafaga.backend.identity.domain.model.User;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final AuthenticationPort authenticationPort;
    private final IdentityStore identityStore;
    private final RegistrationTokenValidator registrationTokenValidator;
    private final Clock clock;

    @Autowired
    public UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator) {
        this(authenticationPort, identityStore, registrationTokenValidator, Clock.systemUTC());
    }

    UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator,
            Clock clock) {
        this.authenticationPort = authenticationPort;
        this.identityStore = identityStore;
        this.registrationTokenValidator = registrationTokenValidator;
        this.clock = clock;
    }

    @Transactional
    public AuthenticationTokens authenticateUser(String username, String password) {
        AuthenticationTokens tokens = authenticationPort.login(username, password);
        synchronizeProfile(authenticationPort.getUserInfo(tokens.accessToken()));
        return tokens;
    }

    @Transactional
    public UserProfile registerUser(String registrationToken, UserRegistration registration) {
        registrationTokenValidator.validate(registrationToken);
        return synchronizeProfile(authenticationPort.registerUser(currentTenantId(), registration));
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, Permission permission) {
        return findUser(userId).getRoles().stream().anyMatch(role -> role.grants(permission));
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentUserPermission(Permission permission) {
        return findUser(currentKeycloakId()).getRoles().stream().anyMatch(role -> role.grants(permission));
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> getUserRoles(UUID userId) {
        return findUser(userId).getRoles().stream().map(RoleSummary::from).toList();
    }

    @Transactional
    public void assignRoleToUser(UUID userId, UUID roleId) {
        String tenantId = currentTenantId();
        User user = findUser(tenantId, userId);
        Role role = identityStore.findRoleById(tenantId, roleId)
                .orElseThrow(() -> new IdentityResourceNotFoundException("Role", roleId));
        user.assignRole(role);
        identityStore.saveUser(user);
    }

    @Transactional(readOnly = true)
    public UserProfile getUserById(UUID userId) {
        return UserProfile.from(findUser(userId));
    }

    @Transactional
    public UserProfile getCurrentUser() {
        Jwt jwt = currentJwt();
        return synchronizeProfile(new IdentityProfile(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("tenant_id")));
    }

    @Transactional
    public UserProfile synchronizeCurrentUser(String accessToken) {
        return synchronizeProfile(authenticationPort.syncUserFromKeycloak(accessToken));
    }

    private UserProfile synchronizeProfile(IdentityProfile profile) {
        String tenantId = currentTenantId();
        verifyTenant(profile.tenantId(), tenantId);
        User user = identityStore.findUserByKeycloakId(tenantId, profile.keycloakId()).orElse(null);
        if (user == null) {
            user = new User(tenantId, profile.keycloakId(), profile.username(), profile.email(), clock.instant());
        } else {
            user.updateProfile(profile.username(), profile.email(), clock.instant());
        }
        return UserProfile.from(identityStore.saveUser(user));
    }

    private User findUser(UUID userId) {
        return findUser(currentTenantId(), userId);
    }

    private User findUser(String tenantId, UUID userId) {
        return identityStore.findUserById(tenantId, userId)
                .orElseThrow(() -> new IdentityResourceNotFoundException("User", userId));
    }

    private User findUser(String keycloakId) {
        String tenantId = currentTenantId();
        return identityStore.findUserByKeycloakId(tenantId, keycloakId)
                .orElseThrow(() -> new IdentityResourceNotFoundException("User", keycloakId));
    }

    private String currentKeycloakId() {
        return currentJwt().getSubject();
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthenticatedUserException();
        }
        return jwt;
    }

    private String currentTenantId() {
        return TenantContext.getCurrentTenantId();
    }

    private void verifyTenant(String profileTenantId, String currentTenantId) {
        if (!currentTenantId.equals(profileTenantId)) {
            throw new CrossTenantAccessException();
        }
    }
}
