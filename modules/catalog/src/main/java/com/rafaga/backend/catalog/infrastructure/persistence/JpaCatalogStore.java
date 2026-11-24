package com.rafaga.backend.catalog.infrastructure.persistence;

import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.application.port.ProductSearchFilter;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class JpaCatalogStore implements CatalogStore {
    private final ProductRepository products;
    private final CategoryRepository categories;

    JpaCatalogStore(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Override
    public Product saveProduct(Product product) { return products.saveAndFlush(product); }

    @Override
    public Optional<Product> findProduct(UUID id, String tenantId) {
        return products.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<Product> findProductBySku(String sku, String tenantId) {
        return products.findBySkuAndTenantId(sku, tenantId);
    }

    @Override
    public long countProducts(String tenantId) {
        return products.countByTenantId(tenantId);
    }

    @Override
    public Page<Product> searchProducts(String tenantId, Pageable pageable) {
        return products.findAllByTenantId(tenantId, pageable);
    }

    @Override
    public Page<Product> searchProducts(String tenantId, ProductSearchFilter filter, Pageable pageable) {
        if (filter == null || filter.isEmpty()) {
            return products.findAllByTenantId(tenantId, pageable);
        }
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (filter.query() != null && !filter.query().isBlank()) {
                String pattern = "%" + filter.query().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern)));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.categoryId() != null) {
                Join<Product, Category> categoryJoin = root.join("categories");
                predicates.add(cb.equal(categoryJoin.get("id"), filter.categoryId()));
                if (query != null) {
                    query.distinct(true);
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return products.findAll(spec, pageable);
    }

    @Override
    public void deleteProduct(Product product) {
        products.delete(product);
        products.flush();
    }

    @Override
    public Category saveCategory(Category category) { return categories.saveAndFlush(category); }

    @Override
    public Optional<Category> findCategory(UUID id, String tenantId) {
        return categories.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<Category> findCategoryBySlug(String slug, String tenantId) {
        return categories.findBySlugAndTenantId(slug, tenantId);
    }

    @Override
    public List<Category> findCategories(String tenantId) {
        return categories.findAllByTenantIdOrderByDisplayOrderAscNameAsc(tenantId);
    }

    @Override
    public Page<Product> findActiveProducts(String tenantId, Pageable pageable) {
        return products.findAllByTenantIdAndStatus(tenantId, ProductStatus.ACTIVE, pageable);
    }

    @Override
    public Optional<Product> findActiveProduct(UUID id, String tenantId) {
        return products.findByIdAndTenantIdAndStatus(id, tenantId, ProductStatus.ACTIVE);
    }
}
