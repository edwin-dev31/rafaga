package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.rafaga.backend.identity.application.exception.UnauthenticatedUserException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.IdentityProfile;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.security.RegistrationTokenValidator;
import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("SUPER_ADMIN", "STORE_MANAGER", "CUSTOMER");

    private final AuthenticationPort authenticationPort;
    private final IdentityStore identityStore;
    private final RegistrationTokenValidator registrationTokenValidator;
    private final TenantLimitsQuery limitsQuery;
    private final TenantRegistry tenantRegistry;
    private final TransactionTemplate requiresNewTransaction;
    private final Clock clock;

    public UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator) {
        this(authenticationPort, identityStore, registrationTokenValidator, null, null, null, Clock.systemUTC());
    }

    @Autowired
    public UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator,
            ObjectProvider<TenantLimitsQuery> limitsQueryProvider,
            ObjectProvider<TenantRegistry> tenantRegistryProvider,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this(
                authenticationPort,
                identityStore,
                registrationTokenValidator,
                limitsQueryProvider.getIfAvailable(),
                tenantRegistryProvider.getIfAvailable(),
                buildRequiresNewTemplate(transactionManagerProvider.getIfAvailable()),
                Clock.systemUTC());
    }

    UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator,
            Clock clock) {
        this(authenticationPort, identityStore, registrationTokenValidator, null, null, null, clock);
    }

    UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator,
            TenantLimitsQuery limitsQuery,
            Clock clock) {
        this(authenticationPort, identityStore, registrationTokenValidator, limitsQuery, null, null, clock);
    }

    UserService(
            AuthenticationPort authenticationPort,
            IdentityStore identityStore,
            RegistrationTokenValidator registrationTokenValidator,
            TenantLimitsQuery limitsQuery,
            TenantRegistry tenantRegistry,
            TransactionTemplate requiresNewTransaction,
            Clock clock) {
        this.authenticationPort = authenticationPort;
        this.identityStore = identityStore;
        this.registrationTokenValidator = registrationTokenValidator;
        this.limitsQuery = limitsQuery;
        this.tenantRegistry = tenantRegistry;
        this.requiresNewTransaction = requiresNewTransaction;
        this.clock = clock;
    }

    private static TransactionTemplate buildRequiresNewTemplate(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            return null;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public AuthenticationTokens authenticateUser(String username, String password) {
        boolean domainBound = TenantContext.isSet();
        String requestedTenantId = domainBound ? TenantContext.getCurrentTenantId() : null;
        try {
            AuthenticationTokens tokens = authenticationPort.login(username, password);
            IdentityProfile profile = authenticationPort.getUserInfo(tokens.accessToken());
            if (domainBound) {
                synchronizeProfileInTransaction(requestedTenantId, profile);
                log.info("event=auth.login outcome=SUCCESS tenantId={}", requestedTenantId);
            } else {
                String profileTenantId = profile.tenantId();
                if (profileTenantId != null && !profileTenantId.isBlank()) {
                    verifyActiveTenant(profileTenantId);
                    synchronizeProfileInTenant(profileTenantId, profile);
                }
                log.info("event=auth.login outcome=SUCCESS mode=dashboard tenantId={}",
                        profileTenantId != null ? profileTenantId : "platform");
            }
            return tokens;
        } catch (InvalidCredentialsException exception) {
            log.warn("event=auth.login outcome=INVALID_CREDENTIALS tenantId={}",
                    requestedTenantId != null ? requestedTenantId : "dashboard");
            throw exception;
        } catch (CrossTenantAccessException exception) {
            log.warn("event=auth.login outcome=CROSS_TENANT_DENIED tenantId={}",
                    requestedTenantId != null ? requestedTenantId : "dashboard");
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.login outcome=IDP_UNAVAILABLE tenantId={}",
                    requestedTenantId != null ? requestedTenantId : "dashboard");
            throw exception;
        }
    }

    public AuthenticationTokens refreshSession(String refreshToken) {
        boolean domainBound = TenantContext.isSet();
        String requestedTenantId = domainBound ? TenantContext.getCurrentTenantId() : null;
        try {
            AuthenticationTokens tokens = authenticationPort.refresh(refreshToken);
            IdentityProfile profile = authenticationPort.getUserInfo(tokens.accessToken());
            if (domainBound) {
                if (profile.tenantId() != null && !requestedTenantId.equals(profile.tenantId())) {
                    throw new CrossTenantAccessException();
                }
            } else if (profile.tenantId() != null && !profile.tenantId().isBlank()) {
                verifyActiveTenant(profile.tenantId());
            }
            log.info("event=auth.refresh outcome=SUCCESS tenantId={}",
                    requestedTenantId != null ? requestedTenantId : profile.tenantId());
            return tokens;
        } catch (InvalidCredentialsException exception) {
            log.warn("event=auth.refresh outcome=INVALID_CREDENTIALS");
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.refresh outcome=IDP_UNAVAILABLE");
            throw exception;
        }
    }

    public void logoutSession(String refreshToken) {
        try {
            authenticationPort.logout(refreshToken);
            log.info("event=auth.logout outcome=SUCCESS");
        } catch (InvalidCredentialsException exception) {
            log.warn("event=auth.logout outcome=INVALID_CREDENTIALS");
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.logout outcome=IDP_UNAVAILABLE");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Page<UserProfile> listTenantUsers(String tenantId, String query, UserStatus status, Pageable pageable) {
        return identityStore.searchUsers(tenantId, query, status, pageable).map(UserProfile::from);
    }

    @Transactional
    public UserProfile registerTenantUser(String tenantId, UserRegistration registration, String requestedRole) {
        String role = normalizeRole(requestedRole);
        if (!"CUSTOMER".equals(role) && limitsQuery != null) {
            limitsQuery.findLimits(tenantId).ifPresent(limits -> {
                long currentUsers = identityStore.countUsers(tenantId);
                if (currentUsers >= limits.maxAdminUsers()) {
                    throw new TenantLimitExceededException("adminUsers", limits.maxAdminUsers(), currentUsers);
                }
            });
        }
        try {
            UserProfile user = synchronizeProfile(tenantId, authenticationPort.registerUser(tenantId, registration, role));
            log.info("event=admin.user_register outcome=SUCCESS tenantId={} role={}", tenantId, role);
            return user;
        } catch (UserAlreadyExistsException exception) {
            log.warn("event=admin.user_register outcome=USER_ALREADY_EXISTS tenantId={}", tenantId);
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=admin.user_register outcome=IDP_UNAVAILABLE tenantId={}", tenantId);
            throw exception;
        }
    }

    @Transactional
    public UserProfile setTenantUserStatus(String tenantId, UUID userId, UserStatus targetStatus) {
        User user = findUser(tenantId, userId);
        boolean enable = targetStatus == UserStatus.ACTIVE;
        authenticationPort.setUserEnabled(user.getKeycloakId(), enable);
        if (enable) {
            user.activate();
        } else {
            user.deactivate();
        }
        User saved = identityStore.saveUser(user);
        log.info("event=admin.user_status outcome=SUCCESS tenantId={} userId={} status={}",
                tenantId, userId, targetStatus);
        return UserProfile.from(saved);
    }

    @Transactional
    public UserProfile registerUser(String registrationToken, UserRegistration registration) {
        String tenantId = currentTenantId();
        try {
            registrationTokenValidator.validate(registrationToken);
            if (limitsQuery != null) {
                limitsQuery.findLimits(tenantId).ifPresent(limits -> {
                    long currentUsers = identityStore.countUsers(tenantId);
                    if (currentUsers >= limits.maxAdminUsers()) {
                        throw new TenantLimitExceededException("adminUsers", limits.maxAdminUsers(), currentUsers);
                    }
                });
            }
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

    @Transactional
    public void changeCurrentUserPassword(String currentPassword, String newPassword) {
        String tenantId = currentTenantId();
        Jwt jwt = currentJwt();
        String jwtTenantId = jwt.getClaimAsString("tenant_id");
        if (jwtTenantId != null && !tenantId.equals(jwtTenantId)) {
            log.warn("event=auth.password_change outcome=CROSS_TENANT_DENIED tenantId={}", tenantId);
            throw new CrossTenantAccessException();
        }
        if (currentPassword == null || currentPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new PasswordPolicyViolationException("Current and new password must not be blank");
        }
        if (currentPassword.equals(newPassword)) {
            throw new PasswordPolicyViolationException("New password must be different from current password");
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = findUser(jwt.getSubject()).getUsername();
        }
        try {
            authenticationPort.login(username, currentPassword);
            authenticationPort.updatePassword(jwt.getSubject(), newPassword, false);
            log.info("event=auth.password_change outcome=SUCCESS tenantId={}", tenantId);
        } catch (InvalidCredentialsException exception) {
            log.warn("event=auth.password_change outcome=INVALID_CREDENTIALS tenantId={}", tenantId);
            throw exception;
        } catch (PasswordPolicyViolationException exception) {
            log.warn("event=auth.password_change outcome=POLICY_VIOLATION tenantId={}", tenantId);
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.password_change outcome=IDP_UNAVAILABLE tenantId={}", tenantId);
            throw exception;
        }
    }

    @Transactional
    public void resetUserPassword(UUID userId, String newPassword, boolean temporary) {
        String tenantId = currentTenantId();
        if (newPassword == null || newPassword.isBlank()) {
            throw new PasswordPolicyViolationException("New password must not be blank");
        }
        User user = findUser(tenantId, userId);
        try {
            authenticationPort.updatePassword(user.getKeycloakId(), newPassword, temporary);
            log.info("event=auth.password_reset outcome=SUCCESS tenantId={} targetUserId={}", tenantId, userId);
        } catch (PasswordPolicyViolationException exception) {
            log.warn("event=auth.password_reset outcome=POLICY_VIOLATION tenantId={} targetUserId={}",
                    tenantId, userId);
            throw exception;
        } catch (IdentityProviderUnavailableException exception) {
            log.warn("event=auth.password_reset outcome=IDP_UNAVAILABLE tenantId={} targetUserId={}",
                    tenantId, userId);
            throw exception;
        }
    }

    private UserProfile synchronizeProfile(IdentityProfile profile) {
        return synchronizeProfile(currentTenantId(), profile);
    }

    private UserProfile synchronizeProfile(String tenantId, IdentityProfile profile) {
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

    private void synchronizeProfileInTransaction(String tenantId, IdentityProfile profile) {
        if (requiresNewTransaction != null) {
            requiresNewTransaction.executeWithoutResult(status -> synchronizeProfile(tenantId, profile));
        } else {
            synchronizeProfile(tenantId, profile);
        }
    }

    private void synchronizeProfileInTenant(String tenantId, IdentityProfile profile) {
        String previousTenantId = TenantContext.isSet() ? TenantContext.getCurrentTenantId() : null;
        TenantContext.setCurrentTenantId(tenantId);
        try {
            synchronizeProfileInTransaction(tenantId, profile);
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(previousTenantId);
            }
        }
    }

    private void verifyActiveTenant(String tenantId) {
        if (tenantRegistry == null) {
            return;
        }
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(CrossTenantAccessException::new);
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
    }

    private String normalizeRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return "STORE_MANAGER";
        }
        String normalized = requestedRole.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported role: " + requestedRole);
        }
        return normalized;
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
