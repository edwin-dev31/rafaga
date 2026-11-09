package com.rafaga.backend.catalog.application.service.storefront;

import java.util.List;
import java.util.UUID;

/**
 * Public-facing availability snapshot for a product variant.
 */
public record StorefrontAvailabilityResponse(
        UUID productId,
        List<VariantAvailability> variants) {

    public record VariantAvailability(
            UUID variantId,
            int availableQuantity,
            boolean inStock) { }
}
