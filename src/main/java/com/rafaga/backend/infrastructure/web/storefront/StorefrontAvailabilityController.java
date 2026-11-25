package com.rafaga.backend.infrastructure.web.storefront;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.application.service.storefront.StorefrontAvailabilityResponse;
import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.inventory.application.service.ProductStockSummaryView;
import com.rafaga.backend.inventory.application.service.StockService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public storefront availability endpoint — bridges catalog and inventory modules.
 */
@RestController
@RequestMapping("/api/storefront/products")
public class StorefrontAvailabilityController {

    private static final CacheControl AVAILABILITY_CACHE = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private final ProductService productService;
    private final StockService stockService;

    public StorefrontAvailabilityController(ProductService productService, StockService stockService) {
        this.productService = productService;
        this.stockService = stockService;
    }

    @GetMapping("/{productId}/availability")
    public ResponseEntity<StorefrontAvailabilityResponse> getAvailability(
            @PathVariable("productId") UUID productId) {
        ProductView product = productService.getProductById(productId);
        ProductStockSummaryView summary = stockService.getProductStockSummary(productId);
        List<StorefrontAvailabilityResponse.VariantAvailability> variants;
        if (product.variants().isEmpty()) {
            int available = summary.baseAvailable();
            variants = List.of(new StorefrontAvailabilityResponse.VariantAvailability(
                    null, available, available > 0));
        } else {
            Map<UUID, Integer> availableByVariant = summary.variants().stream()
                    .collect(Collectors.toMap(
                            ProductStockSummaryView.VariantStockView::variantId,
                            ProductStockSummaryView.VariantStockView::availableQuantity,
                            Integer::sum));
            variants = product.variants().stream()
                    .map(variant -> {
                        int available = availableByVariant.getOrDefault(variant.id(), 0);
                        return new StorefrontAvailabilityResponse.VariantAvailability(
                                variant.id(), available, available > 0);
                    })
                    .toList();
        }
        return ResponseEntity.ok()
                .cacheControl(AVAILABILITY_CACHE)
                .body(new StorefrontAvailabilityResponse(productId, variants));
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    ResponseEntity<ApiError> notFound(CatalogNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", exception.getMessage()));
    }
}
