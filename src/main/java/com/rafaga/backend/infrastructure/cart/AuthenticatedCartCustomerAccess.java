package com.rafaga.backend.infrastructure.cart;

import com.rafaga.backend.cart.application.port.CartCustomerAccess;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.Permission;
import com.rafaga.backend.identity.domain.model.User;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCartCustomerAccess implements CartCustomerAccess {
    private final IdentityStore identities;

    public AuthenticatedCartCustomerAccess(IdentityStore identities) {
        this.identities = identities;
    }

    @Override
    public void requireAccess(UUID customerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Cart access requires an authenticated user");
        }
        User user = identities.findUserByKeycloakId(TenantContext.getCurrentTenantId(), jwt.getSubject())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is not registered"));
        if (customerId.equals(user.getId())
                || user.getRoles().stream().anyMatch(role -> role.grants(Permission.USER_MANAGE))) {
            return;
        }
        throw new AccessDeniedException("Cart belongs to another customer");
    }
}
