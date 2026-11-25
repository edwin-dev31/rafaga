package com.rafaga.backend.catalog.infrastructure.web.storefront;

import com.rafaga.backend.catalog.application.service.storefront.StorefrontCatalogService;
import com.rafaga.backend.catalog.application.service.storefront.StorefrontCategoryResponse;
import com.rafaga.backend.catalog.application.service.storefront.StorefrontProductResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public storefront catalog endpoints — no authentication required.
 * Tenant is resolved from the request host by TenantContextFilter.
 */
@RestController
@RequestMapping("/api/storefront")
public class StorefrontCatalogController {

    private final StorefrontCatalogService service;

    public StorefrontCatalogController(StorefrontCatalogService service) {
        this.service = service;
    }

    private static final CacheControl CATALOG_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();
    private static final CacheControl CATEGORY_CACHE = CacheControl.maxAge(Duration.ofSeconds(120)).cachePublic();
    private static final CacheControl NO_STORE = CacheControl.noStore();

    @GetMapping("/products")
    public ResponseEntity<Page<StorefrontProductResponse>> listProducts(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "categorySlug", required = false) String categorySlug,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok()
                .cacheControl(CATALOG_CACHE)
                .body(service.listProducts(query, categoryId, categorySlug, pageable));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<StorefrontProductResponse> getProduct(
            @PathVariable("productId") UUID productId) {
        return ResponseEntity.ok()
                .cacheControl(CATALOG_CACHE)
                .body(service.getProductById(productId));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<StorefrontCategoryResponse>> getCategories() {
        return ResponseEntity.ok()
                .cacheControl(CATEGORY_CACHE)
                .body(service.getCategoryTree());
    }

    @GetMapping("/categories/{slug}")
    public ResponseEntity<StorefrontCategoryResponse> getCategoryBySlug(
            @PathVariable("slug") String slug) {
        return ResponseEntity.ok()
                .cacheControl(CATEGORY_CACHE)
                .body(service.getCategoryBySlug(slug));
    }
}
