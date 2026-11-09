package com.rafaga.backend.catalog.application.port;

import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CatalogStore {
    Product saveProduct(Product product);
    Optional<Product> findProduct(UUID id, String tenantId);
    Optional<Product> findProductBySku(String sku, String tenantId);
    Page<Product> searchProducts(String tenantId, Pageable pageable);
    void deleteProduct(Product product);
    Category saveCategory(Category category);
    Optional<Category> findCategory(UUID id, String tenantId);
    Optional<Category> findCategoryBySlug(String slug, String tenantId);
    List<Category> findCategories(String tenantId);

    /** Returns only ACTIVE products for the storefront. */
    Page<Product> findActiveProducts(String tenantId, Pageable pageable);

    /** Returns an ACTIVE product by id for the storefront. */
    Optional<Product> findActiveProduct(UUID id, String tenantId);
}
