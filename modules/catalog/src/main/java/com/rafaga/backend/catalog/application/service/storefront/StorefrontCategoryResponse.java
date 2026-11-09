package com.rafaga.backend.catalog.application.service.storefront;

import java.util.List;
import java.util.UUID;

/**
 * Public-facing category tree node for the storefront.
 */
public record StorefrontCategoryResponse(
        UUID id,
        String name,
        String slug,
        List<StorefrontCategoryResponse> children) { }
