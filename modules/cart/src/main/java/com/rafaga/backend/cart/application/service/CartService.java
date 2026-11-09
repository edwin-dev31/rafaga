package com.rafaga.backend.cart.application.service;

import com.rafaga.backend.cart.application.command.UpsertCartItem;
import com.rafaga.backend.cart.application.exception.CartNotFoundException;
import com.rafaga.backend.cart.application.port.CartStore;
import com.rafaga.backend.cart.application.port.CartCustomerAccess;
import com.rafaga.backend.cart.application.service.storefront.GuestSessionService;
import com.rafaga.backend.cart.domain.event.CartCheckedOutEvent;
import com.rafaga.backend.cart.domain.model.Cart;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartStore store;
    private final CartCustomerAccess customerAccess;
    private final ProductService products;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final GuestSessionService guestSessions;

    @Autowired
    public CartService(CartStore store, CartCustomerAccess customerAccess,
            ProductService products, ApplicationEventPublisher events,
            GuestSessionService guestSessions) {
        this(store, customerAccess, products, events, Clock.systemUTC(), guestSessions);
    }

    CartService(CartStore store, CartCustomerAccess customerAccess, ProductService products,
            ApplicationEventPublisher events, Clock clock, GuestSessionService guestSessions) {
        this.store = store;
        this.customerAccess = customerAccess;
        this.products = products;
        this.events = events;
        this.clock = clock;
        this.guestSessions = guestSessions;
    }

    /** Returns the active cart for the customer, creating one if it does not exist. */
    @Transactional
    public CartView getOrCreateCart(UUID customerId) {
        customerAccess.requireAccess(customerId);
        String tenantId = TenantContext.getCurrentTenantId();
        Cart cart = store.findActiveByCustomer(tenantId, customerId)
                .orElseGet(() -> store.save(new Cart(tenantId, customerId, clock.instant())));
        return CartView.from(cart);
    }

    /** Adds a new item or replaces the quantity of an existing item (product + variant). */
    @Transactional
    public CartView upsertItem(UpsertCartItem command) {
        customerAccess.requireAccess(command.customerId());
        String tenantId = TenantContext.getCurrentTenantId();
        BigDecimal unitPrice = resolveUnitPrice(command.productId(), command.variantId());
        Cart cart = store.findActiveByCustomer(tenantId, command.customerId())
                .orElseGet(() -> new Cart(tenantId, command.customerId(), clock.instant()));
        cart.upsertItem(command.productId(), command.variantId(), command.quantity(),
                unitPrice, clock.instant());
        return CartView.from(store.save(cart));
    }

    /** Removes an item from the customer's active cart. */
    @Transactional
    public CartView removeItem(UUID customerId, UUID itemId) {
        customerAccess.requireAccess(customerId);
        String tenantId = TenantContext.getCurrentTenantId();
        Cart cart = activeCart(tenantId, customerId);
        cart.removeItem(itemId, clock.instant());
        return CartView.from(store.save(cart));
    }

    /**
     * Checks out the active cart: transitions status, publishes {@link CartCheckedOutEvent}.
     * Callers (e.g. orders module) listen to that event to create the order.
     */
    @Transactional
    public CartView checkout(UUID customerId) {
        customerAccess.requireAccess(customerId);
        String tenantId = TenantContext.getCurrentTenantId();
        Cart cart = activeCart(tenantId, customerId);
        cart.checkout(clock.instant());
        Cart saved = store.save(cart);
        events.publishEvent(new CartCheckedOutEvent(
                saved.getId(),
                tenantId,
                saved.getCustomerId(),
                saved.getItems().stream()
                        .map(item -> new CartCheckedOutEvent.Line(
                                item.getProductId(), item.getVariantId(),
                                item.getQuantity(), item.getUnitPrice()))
                        .toList()));
        return CartView.from(saved);
    }

    /** Abandons the customer's active cart without creating an order. */
    @Transactional
    public CartView abandon(UUID customerId) {
        customerAccess.requireAccess(customerId);
        String tenantId = TenantContext.getCurrentTenantId();
        Cart cart = activeCart(tenantId, customerId);
        cart.abandon(clock.instant());
        return CartView.from(store.save(cart));
    }

    @Transactional(readOnly = true)
    public CartView getCart(UUID cartId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Cart cart = store.find(cartId, tenantId).orElseThrow(() -> new CartNotFoundException(cartId));
        customerAccess.requireAccess(cart.getCustomerId());
        return CartView.from(cart);
    }

    // -------------------------------------------------------------------------
    // Guest cart operations (storefront)
    // -------------------------------------------------------------------------

    /** Returns or creates the active cart bound to the current guest session. */
    @Transactional
    public CartView getOrCreateGuestCart(String guestSessionId) {
        Optional<String> hash = guestSessions.validateSession(guestSessionId);
        if (hash.isEmpty()) {
            // Create a fresh session when none exists
            guestSessionId = guestSessions.createSession();
            hash = guestSessions.validateSession(guestSessionId);
        }
        String tenantId = TenantContext.getCurrentTenantId();
        String sessionHash = hash.orElseThrow();
        Cart cart = store.findActiveByGuestSession(tenantId, sessionHash)
                .orElseGet(() -> store.save(new Cart(tenantId, sessionHash, clock.instant())));
        return CartView.from(cart);
    }

    /** Upserts an item in the guest's active cart. */
    @Transactional
    public CartView guestUpsertItem(String guestSessionId, UUID productId, UUID variantId,
            int quantity) {
        String tenantId = TenantContext.getCurrentTenantId();
        String sessionHash = requireGuestSession(guestSessionId);
        BigDecimal unitPrice = resolveUnitPrice(productId, variantId);
        Cart cart = store.findActiveByGuestSession(tenantId, sessionHash)
                .orElseGet(() -> new Cart(tenantId, sessionHash, clock.instant()));
        cart.upsertItem(productId, variantId, quantity, unitPrice, clock.instant());
        return CartView.from(store.save(cart));
    }

    /** Removes an item from the guest's active cart. */
    @Transactional
    public CartView guestRemoveItem(String guestSessionId, UUID itemId) {
        String tenantId = TenantContext.getCurrentTenantId();
        String sessionHash = requireGuestSession(guestSessionId);
        Cart cart = store.findActiveByGuestSession(tenantId, sessionHash)
                .orElseThrow(() -> new CartNotFoundException("Guest cart"));
        cart.removeItem(itemId, clock.instant());
        return CartView.from(store.save(cart));
    }

    /** Checks out the guest's active cart. */
    @Transactional
    public CartView guestCheckout(String guestSessionId) {
        String tenantId = TenantContext.getCurrentTenantId();
        String sessionHash = requireGuestSession(guestSessionId);
        Cart cart = store.findActiveByGuestSession(tenantId, sessionHash)
                .orElseThrow(() -> new CartNotFoundException(sessionHash));
        cart.checkout(clock.instant());
        Cart saved = store.save(cart);
        events.publishEvent(new CartCheckedOutEvent(
                saved.getId(), tenantId, null,
                saved.getItems().stream()
                        .map(item -> new CartCheckedOutEvent.Line(
                                item.getProductId(), item.getVariantId(),
                                item.getQuantity(), item.getUnitPrice()))
                        .toList()));
        return CartView.from(saved);
    }

    /** Merges the guest cart into the customer's active cart after login. */
    @Transactional
    public CartView mergeGuestIntoCustomer(String guestSessionId, UUID customerId) {
        customerAccess.requireAccess(customerId);
        String tenantId = TenantContext.getCurrentTenantId();
        String sessionHash = requireGuestSession(guestSessionId);

        Optional<Cart> guestCart = store.findActiveByGuestSession(tenantId, sessionHash);
        if (guestCart.isEmpty()) {
            return getOrCreateCart(customerId);
        }

        Cart customerCart = store.findActiveByCustomer(tenantId, customerId)
                .orElseGet(() -> store.save(new Cart(tenantId, customerId, clock.instant())));
        customerCart.mergeCart(guestCart.get(), clock.instant());
        Cart saved = store.save(customerCart);

        // Abandon the guest cart after merge
        guestCart.get().abandon(clock.instant());
        store.save(guestCart.get());
        return CartView.from(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Cart activeCart(String tenantId, UUID customerId) {
        return store.findActiveByCustomer(tenantId, customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));
    }

    private BigDecimal resolveUnitPrice(UUID productId, UUID variantId) {
        ProductView product = products.getProductById(productId);
        if (variantId == null) return product.basePrice();
        return product.variants().stream()
                .filter(v -> variantId.equals(v.id()))
                .findFirst()
                .map(v -> product.basePrice().add(v.priceAdjustment()))
                .orElseThrow(() -> new CatalogNotFoundException("Product variant", variantId));
    }

    private String requireGuestSession(String guestSessionId) {
        Optional<String> hash = guestSessions.validateSession(guestSessionId);
        return hash.orElseThrow(() -> new CartNotFoundException("Guest session"));
    }
}
