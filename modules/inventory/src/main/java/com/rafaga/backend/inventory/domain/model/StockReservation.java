package com.rafaga.backend.inventory.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "stock_reservations")
@Filter(name = "inventoryTenant", condition = "tenant_id = :tenantId")
public class StockReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "variant_id", updatable = false)
    private UUID variantId;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected StockReservation() { }

    public StockReservation(String tenantId, UUID productId, UUID variantId, int quantity,
            String referenceId, Instant now, Instant expiresAt) {
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive");
        this.tenantId = Objects.requireNonNull(tenantId);
        this.productId = Objects.requireNonNull(productId);
        this.variantId = variantId;
        this.quantity = quantity;
        this.referenceId = Objects.requireNonNull(referenceId);
        this.createdAt = Objects.requireNonNull(now);
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.ACTIVE;
    }

    public void release() { transition(ReservationStatus.RELEASED); }
    public void expire() { transition(ReservationStatus.EXPIRED); }
    public void confirm() { transition(ReservationStatus.CONFIRMED); }

    private void transition(ReservationStatus next) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not active");
        }
        status = next;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public UUID getVariantId() { return variantId; }
    public int getQuantity() { return quantity; }
    public String getReferenceId() { return referenceId; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
