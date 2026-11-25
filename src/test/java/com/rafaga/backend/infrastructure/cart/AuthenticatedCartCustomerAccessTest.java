package com.rafaga.backend.infrastructure.cart;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.service.CustomerAccess;
import com.rafaga.backend.identity.application.service.IdentityAccessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedCartCustomerAccessTest {
    private static final String TENANT = "tenant-a";
    private final IdentityAccessService identities = mock(IdentityAccessService.class);
    private final AuthenticatedCartCustomerAccess access = new AuthenticatedCartCustomerAccess(identities);

    @BeforeEach
    void authenticate() {
        TenantContext.setCurrentTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt()));
    }

    private void authenticateWithRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("keycloak-a").claim("tenant_id", TENANT).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void ownerCanAccessOwnCart() {
        UUID customerId = UUID.randomUUID();
        when(identities.findCustomerAccess(TENANT, "keycloak-a"))
                .thenReturn(Optional.of(new CustomerAccess(customerId)));

        assertThatCode(() -> access.requireAccess(customerId)).doesNotThrowAnyException();
    }

    @Test
    void anotherCustomerCannotAccessCart() {
        when(identities.findCustomerAccess(TENANT, "keycloak-a"))
                .thenReturn(Optional.of(new CustomerAccess(UUID.randomUUID())));

        assertThatThrownBy(() -> access.requireAccess(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void storeManagerCanAccessAssistedCartForCustomerInSameTenant() {
        authenticateWithRole("STORE_MANAGER");
        UUID customerId = UUID.randomUUID();
        when(identities.belongsToTenant(TENANT, customerId)).thenReturn(true);

        assertThatCode(() -> access.requireAccess(customerId)).doesNotThrowAnyException();
    }

    @Test
    void storeManagerCannotAccessCartForCustomerOutsideCurrentTenant() {
        authenticateWithRole("STORE_MANAGER");
        UUID foreignCustomerId = UUID.randomUUID();
        when(identities.belongsToTenant(TENANT, foreignCustomerId)).thenReturn(false);

        assertThatThrownBy(() -> access.requireAccess(foreignCustomerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void storeManagerCannotAccessCartWhenJwtTenantDiffersFromCurrentTenant() {
        Jwt mismatchedJwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject("keycloak-b").claim("tenant_id", "tenant-b").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                mismatchedJwt, List.of(new SimpleGrantedAuthority("ROLE_STORE_MANAGER"))));

        assertThatThrownBy(() -> access.requireAccess(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdminCanAccessAnotherCustomersCart() {
        authenticateWithRole("SUPER_ADMIN");

        assertThatCode(() -> access.requireAccess(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void missingLocalIdentityCannotAccessCart() {
        when(identities.findCustomerAccess(TENANT, "keycloak-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireAccess(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
