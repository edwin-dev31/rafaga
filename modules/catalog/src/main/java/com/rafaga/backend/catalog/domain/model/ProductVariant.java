package com.rafaga.backend.catalog.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "product_variants",
        indexes = @Index(name = "idx_product_variants_tenant_id", columnList = "tenant_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_variants_tenant_sku", columnNames = {"tenant_id", "sku"}))
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @ElementCollection
    @CollectionTable(name = "variant_attributes", joinColumns = @JoinColumn(name = "variant_id"))
    @MapKeyColumn(name = "attribute_name", length = 100)
    @Column(name = "attribute_value", nullable = false, length = 255)
    private Map<String, String> attributes = new LinkedHashMap<>();

    @Column(name = "price_adjustment", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAdjustment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VariantStatus status;

    protected ProductVariant() {
    }

    public ProductVariant(Product product, String sku, String name,
            Map<String, String> attributes, BigDecimal priceAdjustment) {
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.tenantId = product.getTenantId();
        this.sku = Objects.requireNonNull(sku, "sku must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.attributes = new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes must not be null"));
        this.priceAdjustment = Objects.requireNonNull(priceAdjustment, "priceAdjustment must not be null");
        this.status = VariantStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public Map<String, String> getAttributes() { return Map.copyOf(attributes); }
    public BigDecimal getPriceAdjustment() { return priceAdjustment; }
    public VariantStatus getStatus() { return status; }
}
