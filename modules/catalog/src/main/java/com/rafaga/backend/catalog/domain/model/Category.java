package com.rafaga.backend.catalog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Filter(name = "catalogTenant", condition = "tenant_id = :tenantId")
@Table(
        name = "categories",
        indexes = @Index(name = "idx_categories_tenant_id", columnList = "tenant_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categories_tenant_slug", columnNames = {"tenant_id", "slug"}))
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent")
    private List<Category> children = new ArrayList<>();

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Category() {
    }

    public Category(String tenantId, String name, String slug, Category parent, int displayOrder) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        update(name, slug, parent, displayOrder);
    }

    public void update(String name, String slug, Category parent, int displayOrder) {
        if (parent != null && !tenantId.equals(parent.getTenantId())) {
            throw new IllegalArgumentException("Parent category belongs to another tenant");
        }
        if (parent == this) {
            throw new IllegalArgumentException("A category cannot be its own parent");
        }
        for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (id != null && id.equals(ancestor.getId())) {
                throw new IllegalArgumentException("A category cannot descend from itself");
            }
        }
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.slug = Objects.requireNonNull(slug, "slug must not be null");
        this.parent = parent;
        this.displayOrder = displayOrder;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Category getParent() { return parent; }
    public List<Category> getChildren() { return Collections.unmodifiableList(children); }
    public int getDisplayOrder() { return displayOrder; }
}
