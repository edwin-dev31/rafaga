package com.rafaga.backend.catalog.application.service.storefront;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public-facing product summary for the storefront.
 * Intentionally omits tenantId, internal status, audit timestamps, and cost data.
 */
public record StorefrontProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        boolean available,
        List<CategorySummary> categories,
        List<VariantSummary> variants) {

    public record CategorySummary(String name, String slug) { }

    public record VariantSummary(
            UUID id,
            String sku,
            String name,
            BigDecimal price,
            boolean available) { }
}
