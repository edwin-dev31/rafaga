package com.rafaga.backend.infrastructure.cart;

import com.rafaga.backend.cart.application.port.CartCustomerAccess;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.service.IdentityAccessService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCartCustomerAccess implements CartCustomerAccess {
    private final IdentityAccessService identities;

    public AuthenticatedCartCustomerAccess(IdentityAccessService identities) {
        this.identities = identities;
    }

    @Override
    public void requireAccess(UUID customerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Cart access requires an authenticated user");
        }
        if (isSuperAdmin(authentication)) {
            return;
        }
        String tenantId = TenantContext.getCurrentTenantId();
        if (isStoreManager(authentication)) {
            if (!tenantId.equals(jwt.getClaimAsString("tenant_id"))) {
                throw new AccessDeniedException("Store manager cannot access carts of another tenant");
            }
            if (!identities.belongsToTenant(tenantId, customerId)) {
                throw new AccessDeniedException("Customer does not belong to current tenant");
            }
            return;
        }
        var user = identities.findCustomerAccess(tenantId, jwt.getSubject())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is not registered"));
        if (customerId.equals(user.userId())) {
            return;
        }
        throw new AccessDeniedException("Cart belongs to another customer");
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    private boolean isStoreManager(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STORE_MANAGER".equals(authority.getAuthority()));
    }
}
