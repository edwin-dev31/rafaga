package com.rafaga.backend.infrastructure.web.storefront;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.application.service.storefront.StorefrontAvailabilityResponse;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.application.service.StockLevelView;
import com.rafaga.backend.inventory.application.service.StockService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
        List<StorefrontAvailabilityResponse.VariantAvailability> variants = product.variants().stream()
                .map(variant -> {
                    StockLevelView stock = stockService.getStockLevel(productId, variant.id());
                    return new StorefrontAvailabilityResponse.VariantAvailability(
                            variant.id(),
                            stock.availableQuantity(),
                            stock.availableQuantity() > 0);
                })
                .toList();
        // Include base product level if no variants
        if (variants.isEmpty()) {
            try {
                StockLevelView stock = stockService.getStockLevel(productId, null);
                variants = List.of(new StorefrontAvailabilityResponse.VariantAvailability(
                        null, stock.availableQuantity(), stock.availableQuantity() > 0));
            } catch (StockNotFoundException ignored) {
                variants = List.of(new StorefrontAvailabilityResponse.VariantAvailability(
                        null, 0, false));
            }
        }
        return ResponseEntity.ok()
                .cacheControl(AVAILABILITY_CACHE)
                .body(new StorefrontAvailabilityResponse(productId, variants));
    }
}
