package com.rafaga.backend.inventory.domain.model;

import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "stock_levels")
@FilterDef(name = "inventoryTenant", parameters = @ParamDef(
        name = "tenantId", type = String.class, resolver = CurrentInventoryTenantIdSupplier.class),
        autoEnabled = true, applyToLoadByKey = true)
@Filter(name = "inventoryTenant", condition = "tenant_id = :tenantId")
public class StockLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "variant_id", updatable = false)
    private UUID variantId;
    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;
    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;
    @Column(name = "reorder_quantity", nullable = false)
    private int reorderQuantity;
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    protected StockLevel() { }

    public StockLevel(String tenantId, UUID productId, UUID variantId, Instant now) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.productId = Objects.requireNonNull(productId);
        this.variantId = variantId;
        this.lastUpdated = Objects.requireNonNull(now);
    }

    public boolean canReserve(int quantity) {
        return quantity > 0 && availableQuantity >= quantity;
    }

    public void reserve(int quantity, Instant now) {
        if (!canReserve(quantity)) {
            throw new InsufficientStockException("Not enough available stock");
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        lastUpdated = Objects.requireNonNull(now);
    }

    public void release(int quantity, Instant now) {
        requireReserved(quantity);
        reservedQuantity -= quantity;
        availableQuantity += quantity;
        lastUpdated = Objects.requireNonNull(now);
    }

    public void confirm(int quantity, Instant now) {
        requireReserved(quantity);
        reservedQuantity -= quantity;
        totalQuantity -= quantity;
        lastUpdated = Objects.requireNonNull(now);
    }

    public void adjust(int delta, Instant now) {
        if (delta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
        if (delta < 0 && availableQuantity < -(long) delta) {
            throw new InsufficientStockException("Adjustment exceeds available stock");
        }
        totalQuantity = Math.addExact(totalQuantity, delta);
        availableQuantity = Math.addExact(availableQuantity, delta);
        lastUpdated = Objects.requireNonNull(now);
    }

    public void configureReorder(int level, int quantity) {
        if (level < 0 || quantity < 0) throw new IllegalArgumentException("Reorder values must not be negative");
        reorderLevel = level;
        reorderQuantity = quantity;
    }

    private void requireReserved(int quantity) {
        if (quantity <= 0 || reservedQuantity < quantity) {
            throw new IllegalArgumentException("Quantity exceeds reserved stock");
        }
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public UUID getVariantId() { return variantId; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getReorderLevel() { return reorderLevel; }
    public int getReorderQuantity() { return reorderQuantity; }
    public Instant getLastUpdated() { return lastUpdated; }
}
