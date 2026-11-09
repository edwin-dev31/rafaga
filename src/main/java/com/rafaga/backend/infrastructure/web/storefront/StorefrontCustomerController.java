package com.rafaga.backend.infrastructure.web.storefront;

import com.rafaga.backend.cart.application.service.CartService;
import com.rafaga.backend.cart.application.service.CartView;
import com.rafaga.backend.cart.application.service.storefront.GuestSessionService;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.User;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront endpoints for authenticated customers.
 * Resolves cart and order ownership from the authenticated principal, never from the client.
 */
@RestController
@RequestMapping("/api/storefront/customer")
public class StorefrontCustomerController {

    private static final String GUEST_SESSION_COOKIE = "rafaga_guest_session";
    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final CartService cartService;
    private final GuestSessionService guestSessionService;
    private final IdentityStore identityStore;

    public StorefrontCustomerController(CartService cartService,
            GuestSessionService guestSessionService, IdentityStore identityStore) {
        this.cartService = cartService;
        this.guestSessionService = guestSessionService;
        this.identityStore = identityStore;
    }

    /**
     * Returns the authenticated customer's active cart.
     * If a guest session cookie is present, merges the guest cart into the customer cart.
     */
    @GetMapping("/carts/current")
    public ResponseEntity<CartView> getCurrentCart(
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        UUID customerId = resolveCustomerId();

        // Merge guest cart if present
        if (guestSessionId != null && guestSessionService.touchSession(guestSessionId)) {
            CartView merged = cartService.mergeGuestIntoCustomer(guestSessionId, customerId);
            // Clear the guest session cookie after merge
            response.addHeader("Set-Cookie", GUEST_SESSION_COOKIE + "=; Max-Age=0; Path=/; HttpOnly; Secure");
            return ResponseEntity.ok().cacheControl(NO_STORE).body(merged);
        }
        CartView cart = cartService.getOrCreateCart(customerId);
        return ResponseEntity.ok().cacheControl(NO_STORE).body(cart);
    }

    /**
     * Validates the authenticated customer's tenant matches the host-resolved tenant.
     * Returns the resolved customer ID.
     */
    @PostMapping("/validate")
    public ResponseEntity<Void> validateCustomer() {
        resolveCustomerId(); // throws if not authenticated or tenant mismatch
        return ResponseEntity.ok().cacheControl(NO_STORE).build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID resolveCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Customer operations require authentication");
        }
        // Validate tenant claim matches host-resolved tenant
        String hostTenant = TenantContext.getCurrentTenantId();
        String jwtTenant = jwt.getClaimAsString("tenant_id");
        if (jwtTenant == null || !hostTenant.equals(jwtTenant)) {
            throw new AccessDeniedException("Authenticated tenant does not match request tenant");
        }
        // Resolve the user from identity store
        User user = identityStore.findUserByKeycloakId(hostTenant, jwt.getSubject())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is not registered"));
        return user.getId();
    }
}
