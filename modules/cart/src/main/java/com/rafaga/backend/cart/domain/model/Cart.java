package com.rafaga.backend.cart.domain.model;

import com.rafaga.backend.cart.domain.exception.InvalidCartOperationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "carts")
@FilterDef(name = "cartTenant", parameters = @ParamDef(
        name = "tenantId", type = String.class, resolver = CurrentCartTenantIdSupplier.class),
        autoEnabled = true, applyToLoadByKey = true)
@Filter(name = "cartTenant", condition = "tenant_id = :tenantId")
public class Cart {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CartStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() { }

    public Cart(String tenantId, UUID customerId, Instant now) {
        id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        createdAt = Objects.requireNonNull(now);
        updatedAt = now;
        status = CartStatus.ACTIVE;
    }

    /**
     * Adds a new item or replaces the quantity of an existing one (same product + variant).
     * Throws {@link InvalidCartOperationException} when the cart is not active.
     */
    public void upsertItem(UUID productId, UUID variantId, int quantity,
            BigDecimal unitPrice, Instant now) {
        requireActive();
        Optional<CartItem> existing = findItem(productId, variantId);
        if (existing.isPresent()) {
            existing.get().update(quantity, unitPrice);
        } else {
            items.add(new CartItem(this, productId, variantId, quantity, unitPrice));
        }
        updatedAt = Objects.requireNonNull(now);
    }

    /** Removes an item by its id. No-op if the item does not belong to this cart. */
    public void removeItem(UUID itemId, Instant now) {
        requireActive();
        items.removeIf(item -> item.getId() != null && item.getId().equals(itemId));
        updatedAt = Objects.requireNonNull(now);
    }

    /** Transitions the cart to CHECKED_OUT and records the checkout timestamp. */
    public void checkout(Instant now) {
        requireActive();
        if (items.isEmpty()) throw new InvalidCartOperationException("Cannot check out an empty cart");
        status = CartStatus.CHECKED_OUT;
        checkedOutAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    /** Marks the cart as ABANDONED. */
    public void abandon(Instant now) {
        requireActive();
        status = CartStatus.ABANDONED;
        updatedAt = Objects.requireNonNull(now);
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void requireActive() {
        if (status != CartStatus.ACTIVE) {
            throw new InvalidCartOperationException("Cart is not active (status=" + status + ")");
        }
    }

    private Optional<CartItem> findItem(UUID productId, UUID variantId) {
        return items.stream().filter(item ->
                item.getProductId().equals(productId)
                        && Objects.equals(item.getVariantId(), variantId))
                .findFirst();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public CartStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public List<CartItem> getItems() { return Collections.unmodifiableList(items); }
}
