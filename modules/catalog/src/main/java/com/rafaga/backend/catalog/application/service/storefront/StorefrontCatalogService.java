package com.rafaga.backend.catalog.application.service.storefront;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.application.service.CategoryNode;
import com.rafaga.backend.catalog.application.service.CategoryService;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service that exposes only ACTIVE products and categories as public DTOs.
 * Internal fields such as tenantId, cost, status, and audit timestamps are omitted.
 */
@Service
public class StorefrontCatalogService {

    private final CatalogStore store;
    private final CategoryService categoryService;

    public StorefrontCatalogService(CatalogStore store, CategoryService categoryService) {
        this.store = store;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public Page<StorefrontProductResponse> listProducts(Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        return store.findActiveProducts(tenantId, pageable)
                .map(this::toStorefrontProduct);
    }

    @Transactional(readOnly = true)
    public StorefrontProductResponse getProductById(UUID productId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Product product = store.findActiveProduct(productId, tenantId)
                .orElseThrow(() -> new CatalogNotFoundException("Product", productId));
        return toStorefrontProduct(product);
    }

    @Transactional(readOnly = true)
    public List<StorefrontCategoryResponse> getCategoryTree() {
        return categoryService.getCategoryTree().stream()
                .map(this::toStorefrontCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    public StorefrontCategoryResponse getCategoryBySlug(String slug) {
        String tenantId = TenantContext.getCurrentTenantId();
        Category category = store.findCategoryBySlug(slug, tenantId)
                .orElseThrow(() -> new RuntimeException("Category not found: " + slug));
        return toStorefrontCategoryFromEntity(category);
    }

    // -------------------------------------------------------------------------
    // Mappings
    // -------------------------------------------------------------------------

    private StorefrontProductResponse toStorefrontProduct(Product product) {
        return new StorefrontProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                true,
                product.getCategories().stream()
                        .map(cat -> new StorefrontProductResponse.CategorySummary(
                                cat.getName(), cat.getSlug()))
                        .toList(),
                product.getVariants().stream()
                        .map(variant -> new StorefrontProductResponse.VariantSummary(
                                variant.getId(),
                                variant.getSku(),
                                variant.getName(),
                                product.getBasePrice().add(variant.getPriceAdjustment()),
                                variant.getStatus() == VariantStatus.ACTIVE))
                        .toList());
    }

    private StorefrontCategoryResponse toStorefrontCategory(CategoryNode node) {
        return new StorefrontCategoryResponse(
                node.id(),
                node.name(),
                node.slug(),
                node.children().stream()
                        .map(this::toStorefrontCategory)
                        .toList());
    }

    private StorefrontCategoryResponse toStorefrontCategoryFromEntity(Category category) {
        return new StorefrontCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                List.of());
    }
}
