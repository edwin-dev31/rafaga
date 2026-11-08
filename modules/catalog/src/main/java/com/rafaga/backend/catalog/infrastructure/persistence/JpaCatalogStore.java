package com.rafaga.backend.catalog.infrastructure.persistence;

import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<Product> searchProducts(String tenantId, Pageable pageable) {
        return products.findAllByTenantId(tenantId, pageable);
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
}
