package com.rafaga.backend.catalog.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@FilterDef(name = "catalogTenant", parameters = @ParamDef(
        name = "tenantId", type = String.class, resolver = CurrentTenantIdSupplier.class),
        autoEnabled = true, applyToLoadByKey = true)
@Filter(name = "catalogTenant", condition = "tenant_id = :tenantId")
@Table(
        name = "products",
        indexes = @Index(name = "idx_products_tenant_id", columnList = "tenant_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_products_tenant_sku", columnNames = {"tenant_id", "sku"}))
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 5000)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String tenantId, String sku, String name, String description,
            BigDecimal basePrice, Instant now) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sku = Objects.requireNonNull(sku, "sku must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.status = ProductStatus.DRAFT;
        update(name, description, basePrice, status, now);
    }

    public void update(String name, String description, BigDecimal basePrice,
            ProductStatus status, Instant now) {
        Objects.requireNonNull(basePrice, "basePrice must not be null");
        if (basePrice.signum() < 0) {
            throw new IllegalArgumentException("basePrice must not be negative");
        }
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.basePrice = basePrice;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void addCategory(Category category) {
        Objects.requireNonNull(category, "category must not be null");
        if (!tenantId.equals(category.getTenantId())) {
            throw new IllegalArgumentException("Category belongs to another tenant");
        }
        categories.add(category);
    }

    public void replaceCategories(Set<Category> replacements) {
        Objects.requireNonNull(replacements, "replacements must not be null");
        for (Category category : replacements) {
            if (!tenantId.equals(category.getTenantId())) {
                throw new IllegalArgumentException("Category belongs to another tenant");
            }
        }
        categories.clear();
        categories.addAll(replacements);
    }

    public void addVariant(ProductVariant variant) {
        Objects.requireNonNull(variant, "variant must not be null");
        if (variant.getProduct() != this) {
            throw new IllegalArgumentException("Variant belongs to another product");
        }
        variants.add(variant);
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getBasePrice() { return basePrice; }
    public ProductStatus getStatus() { return status; }
    public Set<Category> getCategories() { return Collections.unmodifiableSet(categories); }
    public List<ProductVariant> getVariants() { return Collections.unmodifiableList(variants); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
