package com.rafaga.backend.cart.application.service;

import com.rafaga.backend.cart.application.command.UpsertCartItem;
import com.rafaga.backend.cart.application.exception.CartNotFoundException;
import com.rafaga.backend.cart.application.port.CartStore;
import com.rafaga.backend.cart.application.port.CartCustomerAccess;
import com.rafaga.backend.cart.domain.event.CartCheckedOutEvent;
import com.rafaga.backend.cart.domain.model.Cart;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
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

    @Autowired
    public CartService(CartStore store, CartCustomerAccess customerAccess,
            ProductService products, ApplicationEventPublisher events) {
        this(store, customerAccess, products, events, Clock.systemUTC());
    }

    CartService(CartStore store, CartCustomerAccess customerAccess, ProductService products,
            ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.customerAccess = customerAccess;
        this.products = products;
        this.events = events;
        this.clock = clock;
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
}
