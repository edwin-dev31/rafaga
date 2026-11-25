package com.rafaga.backend.catalog.application.service.storefront;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.application.port.ProductSearchFilter;
import com.rafaga.backend.catalog.application.port.StorefrontProductImageLookup;
import com.rafaga.backend.catalog.application.service.CategoryNode;
import com.rafaga.backend.catalog.application.service.CategoryService;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.List;
import java.util.Map;
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
    private final StorefrontProductImageLookup productImageLookup;

    public StorefrontCatalogService(
            CatalogStore store,
            CategoryService categoryService,
            StorefrontProductImageLookup productImageLookup) {
        this.store = store;
        this.categoryService = categoryService;
        this.productImageLookup = productImageLookup;
    }

    @Transactional(readOnly = true)
    public Page<StorefrontProductResponse> listProducts(Pageable pageable) {
        return listProducts(null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StorefrontProductResponse> listProducts(
            String query,
            UUID categoryId,
            String categorySlug,
            Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        boolean hasQuery = query != null && !query.isBlank();
        boolean hasCategorySlug = categorySlug != null && !categorySlug.isBlank();
        Page<Product> products;
        if (!hasQuery && categoryId == null && !hasCategorySlug) {
            products = store.findActiveProducts(tenantId, pageable);
        } else {
            UUID effectiveCategoryId = categoryId;
            if (effectiveCategoryId == null && hasCategorySlug) {
                var resolved = store.findCategoryBySlug(categorySlug.trim(), tenantId);
                if (resolved.isEmpty()) {
                    return Page.empty(pageable);
                }
                effectiveCategoryId = resolved.get().getId();
            }
            products = store.searchProducts(
                    tenantId,
                    new ProductSearchFilter(hasQuery ? query.trim() : null, ProductStatus.ACTIVE, effectiveCategoryId),
                    pageable);
        }
        Map<UUID, List<StorefrontProductImageLookup.Image>> imagesByProduct =
                productImageLookup.findByProductIds(products.getContent().stream()
                        .map(Product::getId)
                        .toList());
        return products.map(product -> toStorefrontProduct(
                product, imagesByProduct.getOrDefault(product.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public StorefrontProductResponse getProductById(UUID productId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Product product = store.findActiveProduct(productId, tenantId)
                .orElseThrow(() -> new CatalogNotFoundException("Product", productId));
        List<StorefrontProductImageLookup.Image> images = productImageLookup
                .findByProductIds(List.of(productId))
                .getOrDefault(productId, List.of());
        return toStorefrontProduct(product, images);
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
                .orElseThrow(() -> new CatalogNotFoundException("Category", slug));
        return toStorefrontCategoryFromEntity(category);
    }

    // -------------------------------------------------------------------------
    // Mappings
    // -------------------------------------------------------------------------

    private StorefrontProductResponse toStorefrontProduct(
            Product product, List<StorefrontProductImageLookup.Image> images) {
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
                        .toList(),
                images.stream()
                        .map(image -> new StorefrontProductResponse.ImageSummary(
                                image.url(), image.contentType(), image.width(), image.height(),
                                image.position(), image.primary()))
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
