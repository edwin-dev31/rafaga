package com.rafaga.backend.orders.domain.model;

import com.rafaga.backend.orders.domain.exception.InvalidOrderTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "orders")
@FilterDef(name = "orderTenant", parameters = @ParamDef(
        name = "tenantId", type = String.class, resolver = CurrentOrderTenantIdSupplier.class),
        autoEnabled = true, applyToLoadByKey = true)
@Filter(name = "orderTenant", condition = "tenant_id = :tenantId")
public class Order {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;
    @Column(name = "customer_id", updatable = false)
    private UUID customerId;
    @Column(name = "cart_id", updatable = false)
    private UUID cartId;
    @Column(name = "public_token", length = 64, updatable = false)
    private String publicToken;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    protected Order() { }

    public Order(String tenantId, UUID customerId, Instant now) {
        this(tenantId, Objects.requireNonNull(customerId), null, now);
    }

    /** Creates an order sourced from a checked-out cart; customerId may be null for guest checkouts. */
    public static Order fromCart(String tenantId, UUID cartId, UUID customerId, Instant now) {
        return new Order(tenantId, customerId, Objects.requireNonNull(cartId, "cartId must not be null"), now);
    }

    private Order(String tenantId, UUID customerId, UUID cartId, Instant now) {
        id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = customerId;
        this.cartId = cartId;
        this.publicToken = generatePublicToken();
        createdAt = Objects.requireNonNull(now);
        updatedAt = now;
        status = OrderStatus.PENDING;
        subtotal = BigDecimal.ZERO.setScale(2);
        total = subtotal;
    }

    private static String generatePublicToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    public void addLine(UUID productId, UUID variantId, int quantity, BigDecimal unitPrice) {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderTransitionException("Only pending orders can add lines");
        }
        if (lines.stream().anyMatch(line -> line.getProductId().equals(productId)
                && Objects.equals(line.getVariantId(), variantId))) {
            throw new IllegalArgumentException("Duplicate product and variant in order");
        }
        OrderLine line = new OrderLine(this, productId, variantId, quantity, unitPrice);
        lines.add(line);
        subtotal = subtotal.add(line.getLineTotal());
        total = subtotal;
    }

    public void confirm(Instant now) {
        requireStatus(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        status = OrderStatus.CONFIRMED;
        confirmedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void complete(Instant now) {
        requireStatus(OrderStatus.CONFIRMED, OrderStatus.COMPLETED);
        status = OrderStatus.COMPLETED;
        completedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderTransitionException("Cannot cancel an order in status " + status);
        }
        status = OrderStatus.CANCELLED;
        updatedAt = Objects.requireNonNull(now);
    }

    private void requireStatus(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            throw new InvalidOrderTransitionException("Cannot change order from " + status + " to " + next);
        }
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getCartId() { return cartId; }
    public String getPublicToken() { return publicToken; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
}
