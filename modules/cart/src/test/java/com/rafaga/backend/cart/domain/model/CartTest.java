package com.rafaga.backend.cart.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rafaga.backend.cart.domain.exception.InvalidCartOperationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TENANT = "tenant-a";

    private Cart newCart() {
        return new Cart(TENANT, UUID.randomUUID(), NOW);
    }

    @Test
    void upsertAddsNewItem() {
        Cart cart = newCart();
        UUID product = UUID.randomUUID();

        cart.upsertItem(product, null, 2, new BigDecimal("10.00"), NOW);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void upsertReplacesQuantityForExistingProductVariant() {
        Cart cart = newCart();
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();

        cart.upsertItem(product, variant, 1, new BigDecimal("5.00"), NOW);
        cart.upsertItem(product, variant, 3, new BigDecimal("6.00"), NOW);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(3);
        assertThat(cart.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("6.00");
        assertThat(cart.getTotal()).isEqualByComparingTo("18.00");
    }

    @Test
    void differentVariantsTreatedAsSeparateItems() {
        Cart cart = newCart();
        UUID product = UUID.randomUUID();

        cart.upsertItem(product, UUID.randomUUID(), 1, new BigDecimal("8.00"), NOW);
        cart.upsertItem(product, UUID.randomUUID(), 1, new BigDecimal("9.00"), NOW);

        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getTotal()).isEqualByComparingTo("17.00");
    }

    @Test
    void removeItemDropsItFromCart() {
        Cart cart = newCart();
        UUID product = UUID.randomUUID();
        cart.upsertItem(product, null, 1, new BigDecimal("4.50"), NOW);
        UUID itemId = cart.getItems().getFirst().getId();

        cart.removeItem(itemId, NOW);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void checkoutTransitionsStatusAndRecordsTimestamp() {
        Cart cart = newCart();
        cart.upsertItem(UUID.randomUUID(), null, 1, BigDecimal.ONE, NOW);
        cart.checkout(NOW.plusSeconds(60));

        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        assertThat(cart.getCheckedOutAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void checkoutFailsOnEmptyCart() {
        Cart cart = newCart();
        assertThatThrownBy(() -> cart.checkout(NOW))
                .isInstanceOf(InvalidCartOperationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void abandonTransitionsStatusToAbandoned() {
        Cart cart = newCart();
        cart.abandon(NOW.plusSeconds(10));
        assertThat(cart.getStatus()).isEqualTo(CartStatus.ABANDONED);
    }

    @Test
    void operationsRejectedOnCheckedOutCart() {
        Cart cart = newCart();
        cart.upsertItem(UUID.randomUUID(), null, 1, BigDecimal.ONE, NOW);
        cart.checkout(NOW.plusSeconds(1));

        assertThatThrownBy(() -> cart.upsertItem(UUID.randomUUID(), null, 1, BigDecimal.TEN, NOW))
                .isInstanceOf(InvalidCartOperationException.class);
        assertThatThrownBy(() -> cart.abandon(NOW.plusSeconds(2)))
                .isInstanceOf(InvalidCartOperationException.class);
    }

    @Test
    void totalAggregatesAllItemLineTotals() {
        Cart cart = newCart();
        cart.upsertItem(UUID.randomUUID(), null, 3, new BigDecimal("2.50"), NOW);
        cart.upsertItem(UUID.randomUUID(), null, 1, new BigDecimal("7.00"), NOW);

        // 3*2.50 + 1*7.00 = 7.50 + 7.00 = 14.50
        assertThat(cart.getTotal()).isEqualByComparingTo("14.50");
    }
}
