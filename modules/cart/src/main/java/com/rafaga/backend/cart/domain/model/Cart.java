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

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16, updatable = false)
    private OwnerType ownerType;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "guest_session_hash", length = 64, updatable = false)
    private String guestSessionHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CartStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_public_token", length = 64)
    private String orderPublicToken;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() { }

    /** Creates a customer-owned cart. */
    public Cart(String tenantId, UUID customerId, Instant now) {
        id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.ownerType = OwnerType.CUSTOMER;
        this.customerId = Objects.requireNonNull(customerId);
        this.guestSessionHash = null;
        createdAt = Objects.requireNonNull(now);
        updatedAt = now;
        status = CartStatus.ACTIVE;
    }

    /** Creates a guest-session-owned cart. */
    public Cart(String tenantId, String guestSessionHash, Instant now) {
        id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.ownerType = OwnerType.GUEST;
        this.customerId = null;
        this.guestSessionHash = Objects.requireNonNull(guestSessionHash);
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
    public OwnerType getOwnerType() { return ownerType; }
    public UUID getCustomerId() { return customerId; }
    public String getGuestSessionHash() { return guestSessionHash; }
    public CartStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public UUID getOrderId() { return orderId; }
    public String getOrderPublicToken() { return orderPublicToken; }
    public List<CartItem> getItems() { return Collections.unmodifiableList(items); }

    /** Links the created order and its public receipt token to a checked-out cart. */
    public void linkOrder(UUID orderId, String orderPublicToken) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (status != CartStatus.CHECKED_OUT) {
            throw new InvalidCartOperationException("Only checked-out carts can be linked to an order");
        }
        if (this.orderId != null && !this.orderId.equals(orderId)) {
            throw new InvalidCartOperationException("Cart is already linked to another order");
        }
        this.orderId = orderId;
        this.orderPublicToken = orderPublicToken;
    }

    /** Merges the items from another cart into this cart (guest → customer merge). */
    public void mergeCart(Cart other, Instant now) {
        requireActive();
        Objects.requireNonNull(other, "other cart must not be null");
        for (CartItem item : other.getItems()) {
            upsertItem(item.getProductId(), item.getVariantId(), item.getQuantity(),
                    item.getUnitPrice(), now);
        }
    }
}
