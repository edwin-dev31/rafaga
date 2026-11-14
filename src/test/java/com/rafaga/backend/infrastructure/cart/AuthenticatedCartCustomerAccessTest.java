package com.rafaga.backend.infrastructure.cart;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.service.CustomerAccess;
import com.rafaga.backend.identity.application.service.IdentityAccessService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
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
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject("keycloak-a").claim("tenant_id", TENANT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
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
                .thenReturn(Optional.of(new CustomerAccess(customerId, false)));

        assertThatCode(() -> access.requireAccess(customerId)).doesNotThrowAnyException();
    }

    @Test
    void anotherCustomerCannotAccessCart() {
        when(identities.findCustomerAccess(TENANT, "keycloak-a"))
                .thenReturn(Optional.of(new CustomerAccess(UUID.randomUUID(), false)));

        assertThatThrownBy(() -> access.requireAccess(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userManagerCanAccessAnotherCustomersCart() {
        when(identities.findCustomerAccess(TENANT, "keycloak-a"))
                .thenReturn(Optional.of(new CustomerAccess(UUID.randomUUID(), true)));

        assertThatCode(() -> access.requireAccess(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void missingLocalIdentityCannotAccessCart() {
        when(identities.findCustomerAccess(TENANT, "keycloak-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireAccess(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
