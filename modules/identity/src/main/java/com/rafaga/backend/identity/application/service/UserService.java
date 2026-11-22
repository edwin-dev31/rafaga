package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.exception.UnauthenticatedUserException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.security.RegistrationTokenValidator;
import com.rafaga.backend.identity.domain.model.User;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
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
        String tenantId = TenantContext.getCurrentTenantId();
        try {
            AuthenticationTokens tokens = authenticationPort.login(username, password);
            synchronizeProfile(authenticationPort.getUserInfo(tokens.accessToken()));
            log.info("event=auth.login outcome=SUCCESS tenantId={}", tenantId);
            return tokens;
        } catch (InvalidCredentialsException exception) {
            log.warn("event=auth.login outcome=INVALID_CREDENTIALS tenantId={}", tenantId);
            throw exception;
        } catch (CrossTenantAccessException exception) {
            log.warn("event=auth.login outcome=CROSS_TENANT_DENIED tenantId={}", tenantId);
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.login outcome=IDP_UNAVAILABLE tenantId={}", tenantId);
            throw exception;
        }
    }

    @Transactional
    public UserProfile registerUser(String registrationToken, UserRegistration registration) {
        String tenantId = currentTenantId();
        try {
            registrationTokenValidator.validate(registrationToken);
            UserProfile user = synchronizeProfile(authenticationPort.registerUser(tenantId, registration));
            log.info("event=auth.register outcome=SUCCESS tenantId={}", tenantId);
            return user;
        } catch (InvalidRegistrationTokenException exception) {
            log.warn("event=auth.register outcome=INVALID_TOKEN tenantId={}", tenantId);
            throw exception;
        } catch (UserAlreadyExistsException exception) {
            log.warn("event=auth.register outcome=USER_ALREADY_EXISTS tenantId={}", tenantId);
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.register outcome=IDP_UNAVAILABLE tenantId={}", tenantId);
            throw exception;
        }
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
        String profileTenantId = profile.tenantId();
        if (profileTenantId != null && !tenantId.equals(profileTenantId)) {
            throw new CrossTenantAccessException();
        }
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

}
