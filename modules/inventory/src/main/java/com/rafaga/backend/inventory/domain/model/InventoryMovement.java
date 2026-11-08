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
@Table(name = "inventory_movements")
@Filter(name = "inventoryTenant", condition = "tenant_id = :tenantId")
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "variant_id", updatable = false)
    private UUID variantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MovementType type;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "previous_total", nullable = false)
    private int previousTotal;
    @Column(name = "new_total", nullable = false)
    private int newTotal;
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    @Column(length = 1000)
    private String notes;
    @Column(nullable = false)
    private Instant timestamp;

    protected InventoryMovement() { }

    public InventoryMovement(String tenantId, UUID productId, UUID variantId, MovementType type,
            int quantity, int previousTotal, int newTotal, String referenceId, String notes, Instant timestamp) {
        if (quantity <= 0) throw new IllegalArgumentException("Movement quantity must be positive");
        this.tenantId = Objects.requireNonNull(tenantId);
        this.productId = Objects.requireNonNull(productId);
        this.variantId = variantId;
        this.type = Objects.requireNonNull(type);
        this.quantity = quantity;
        this.previousTotal = previousTotal;
        this.newTotal = newTotal;
        this.referenceId = referenceId;
        this.notes = notes;
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public UUID getVariantId() { return variantId; }
    public MovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public int getPreviousTotal() { return previousTotal; }
    public int getNewTotal() { return newTotal; }
    public String getReferenceId() { return referenceId; }
    public String getNotes() { return notes; }
    public Instant getTimestamp() { return timestamp; }
}
