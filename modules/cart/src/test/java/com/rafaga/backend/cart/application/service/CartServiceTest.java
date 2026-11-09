package com.rafaga.backend.cart.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.rafaga.backend.cart.application.command.UpsertCartItem;
import com.rafaga.backend.cart.application.exception.CartNotFoundException;
import com.rafaga.backend.cart.application.port.CartStore;
import com.rafaga.backend.cart.application.port.CartCustomerAccess;
import com.rafaga.backend.cart.application.service.storefront.GuestSessionService;
import com.rafaga.backend.cart.domain.event.CartCheckedOutEvent;
import com.rafaga.backend.cart.domain.model.Cart;
import com.rafaga.backend.cart.domain.model.CartStatus;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String TENANT = "tenant-a";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock private CartStore store;
    @Mock private CartCustomerAccess customerAccess;
    @Mock private ProductService products;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private GuestSessionService guestSessions;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(store, customerAccess, products, publisher, FIXED, guestSessions);
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // getOrCreateCart
    // -------------------------------------------------------------------------

    @Test
    void getOrCreateReturnsExistingActiveCart() {
        UUID customerId = UUID.randomUUID();
        Cart existing = new Cart(TENANT, customerId, NOW.minusSeconds(60));
        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.of(existing));

        CartView view = service.getOrCreateCart(customerId);

        assertThat(view.customerId()).isEqualTo(customerId);
        assertThat(view.status()).isEqualTo(CartStatus.ACTIVE);
    }

    @Test
    void getOrCreateCreatesNewCartWhenNoneExists() {
        UUID customerId = UUID.randomUUID();
        Cart newCart = new Cart(TENANT, customerId, NOW);
        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.empty());
        when(store.save(any(Cart.class))).thenReturn(newCart);

        CartView view = service.getOrCreateCart(customerId);

        assertThat(view.status()).isEqualTo(CartStatus.ACTIVE);
        verify(store).save(any(Cart.class));
    }

    @Test
    void rejectsUnauthorizedCustomerBeforeAccessingTheStore() {
        UUID customerId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Other customer"))
                .when(customerAccess).requireAccess(customerId);

        assertThatThrownBy(() -> service.getOrCreateCart(customerId))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(store);
    }

    // -------------------------------------------------------------------------
    // upsertItem
    // -------------------------------------------------------------------------

    @Test
    void upsertItemAddsLineToExistingCart() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart cart = new Cart(TENANT, customerId, NOW);
        ProductView product = productView(productId, new BigDecimal("10.00"));

        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.of(cart));
        when(products.getProductById(productId)).thenReturn(product);
        when(store.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartView view = service.upsertItem(new UpsertCartItem(customerId, productId, null, 2));

        assertThat(view.items()).hasSize(1);
        assertThat(view.total()).isEqualByComparingTo("20.00");
    }

    @Test
    void upsertItemCreatesCartWhenNoneExists() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart newCart = new Cart(TENANT, customerId, NOW);
        ProductView product = productView(productId, new BigDecimal("5.00"));

        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.empty());
        when(store.save(any(Cart.class))).thenReturn(newCart);
        when(products.getProductById(productId)).thenReturn(product);

        service.upsertItem(new UpsertCartItem(customerId, productId, null, 1));

        verify(store).save(any(Cart.class));
    }

    @Test
    void upsertItemRefreshesExistingPriceAndTotal() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart cart = new Cart(TENANT, customerId, NOW);
        cart.upsertItem(productId, null, 1, new BigDecimal("5.00"), NOW);
        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.of(cart));
        when(products.getProductById(productId)).thenReturn(productView(productId, new BigDecimal("8.00")));
        when(store.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartView view = service.upsertItem(new UpsertCartItem(customerId, productId, null, 2));

        assertThat(view.items().getFirst().unitPrice()).isEqualByComparingTo("8.00");
        assertThat(view.total()).isEqualByComparingTo("16.00");
    }

    // -------------------------------------------------------------------------
    // removeItem
    // -------------------------------------------------------------------------

    @Test
    void removeItemThrowsWhenNoActiveCart() {
        UUID customerId = UUID.randomUUID();
        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(customerId, UUID.randomUUID()))
                .isInstanceOf(CartNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getCart
    // -------------------------------------------------------------------------

    @Test
    void getCartRejectsAnotherCustomerAfterLoadingIt() {
        UUID cartId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Cart cart = new Cart(TENANT, customerId, NOW);
        when(store.find(cartId, TENANT)).thenReturn(Optional.of(cart));
        doThrow(new AccessDeniedException("Other customer"))
                .when(customerAccess).requireAccess(customerId);

        assertThatThrownBy(() -> service.getCart(cartId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // -------------------------------------------------------------------------
    // checkout
    // -------------------------------------------------------------------------

    @Test
    void checkoutPublishesCartCheckedOutEvent() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Cart cart = new Cart(TENANT, customerId, NOW);
        cart.upsertItem(productId, null, 1, new BigDecimal("15.00"), NOW);

        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.of(cart));
        when(store.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        service.checkout(customerId);

        ArgumentCaptor<CartCheckedOutEvent> captor = ArgumentCaptor.forClass(CartCheckedOutEvent.class);
        verify(publisher).publishEvent(captor.capture());

        CartCheckedOutEvent event = captor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.items()).hasSize(1);
        assertThat(event.items().getFirst().productId()).isEqualTo(productId);
    }

    @Test
    void checkoutThrowsWhenNoActiveCart() {
        UUID customerId = UUID.randomUUID();
        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(customerId))
                .isInstanceOf(CartNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // abandon
    // -------------------------------------------------------------------------

    @Test
    void abandonTransitionsCartToAbandoned() {
        UUID customerId = UUID.randomUUID();
        Cart cart = new Cart(TENANT, customerId, NOW);
        when(store.findActiveByCustomer(TENANT, customerId)).thenReturn(Optional.of(cart));
        when(store.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartView view = service.abandon(customerId);

        assertThat(view.status()).isEqualTo(CartStatus.ABANDONED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProductView productView(UUID productId, BigDecimal price) {
        return new ProductView(productId, "SKU-001", "Test Product", "desc",
                price, ProductStatus.ACTIVE, java.util.Set.of(), List.of(), null, null);
    }
}
