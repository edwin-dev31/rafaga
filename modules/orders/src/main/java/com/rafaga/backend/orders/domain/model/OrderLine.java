package com.rafaga.backend.orders.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "order_lines")
@Filter(name = "orderTenant", condition = "tenant_id = :tenantId")
public class OrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "variant_id", updatable = false)
    private UUID variantId;
    @Column(name = "product_name", length = 255, updatable = false)
    private String productName;
    @Column(name = "sku", length = 120, updatable = false)
    private String sku;
    @Column(name = "variant_name", length = 255, updatable = false)
    private String variantName;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    protected OrderLine() { }

    OrderLine(Order order, UUID productId, UUID variantId, int quantity, BigDecimal unitPrice) {
        this(order, productId, variantId, quantity, unitPrice, null, null, null);
    }

    OrderLine(Order order, UUID productId, UUID variantId, int quantity, BigDecimal unitPrice,
            String productName, String sku, String variantName) {
        if (quantity <= 0) throw new IllegalArgumentException("Line quantity must be positive");
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price must not be negative");
        }
        this.order = Objects.requireNonNull(order);
        tenantId = order.getTenantId();
        this.productId = Objects.requireNonNull(productId);
        this.variantId = variantId;
        this.productName = productName;
        this.sku = sku;
        this.variantName = variantName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public Order getOrder() { return order; }
    public UUID getProductId() { return productId; }
    public UUID getVariantId() { return variantId; }
    public String getProductName() { return productName; }
    public String getSku() { return sku; }
    public String getVariantName() { return variantName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
