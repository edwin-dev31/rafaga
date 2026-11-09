package com.rafaga.backend.cart.infrastructure.web.storefront;

import com.rafaga.backend.cart.application.service.CartService;
import com.rafaga.backend.cart.application.service.CartView;
import com.rafaga.backend.cart.application.service.storefront.GuestSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public storefront cart endpoints — no authentication required.
 * Cart ownership is derived from the guest session cookie, never from the request body.
 */
@RestController
@RequestMapping("/api/storefront/carts/current")
public class StorefrontCartController {

    private static final String GUEST_SESSION_COOKIE = "rafaga_guest_session";
    private static final CacheControl NO_STORE = CacheControl.noStore();

    private final CartService cartService;
    private final GuestSessionService guestSessionService;

    public StorefrontCartController(CartService cartService, GuestSessionService guestSessionService) {
        this.cartService = cartService;
        this.guestSessionService = guestSessionService;
    }

    @GetMapping
    public ResponseEntity<CartView> getOrCreate(
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        String sessionId = resolveOrCreateSession(guestSessionId, response);
        CartView cart = cartService.getOrCreateGuestCart(sessionId);
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(cart);
    }

    @PutMapping("/items")
    public ResponseEntity<CartView> upsertItem(
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionId,
            @NotNull @RequestBody UpsertItemRequest request,
            HttpServletResponse response) {
        String sessionId = resolveOrCreateSession(guestSessionId, response);
        CartView cart = cartService.guestUpsertItem(
                sessionId, request.productId(), request.variantId(), request.quantity());
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(cart);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartView> removeItem(
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionId,
            @PathVariable("itemId") UUID itemId,
            HttpServletResponse response) {
        String sessionId = resolveOrCreateSession(guestSessionId, response);
        CartView cart = cartService.guestRemoveItem(sessionId, itemId);
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(cart);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<CartView> checkout(
            @CookieValue(value = GUEST_SESSION_COOKIE, required = false) String guestSessionId,
            HttpServletResponse response) {
        String sessionId = resolveOrCreateSession(guestSessionId, response);
        CartView cart = cartService.guestCheckout(sessionId);
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(cart);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveOrCreateSession(String guestSessionId, HttpServletResponse response) {
        if (guestSessionId != null && guestSessionService.touchSession(guestSessionId)) {
            return guestSessionId;
        }
        String sessionId = guestSessionService.createSession();
        setGuestSessionCookie(response, sessionId);
        return sessionId;
    }

    private void setGuestSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from(GUEST_SESSION_COOKIE, sessionId)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public record UpsertItemRequest(
            @NotNull UUID productId,
            UUID variantId,
            @NotNull @Min(1) Integer quantity) { }
}
