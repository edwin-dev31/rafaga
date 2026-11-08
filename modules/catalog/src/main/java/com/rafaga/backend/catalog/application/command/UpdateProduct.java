package com.rafaga.backend.catalog.application.command;

import com.rafaga.backend.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record UpdateProduct(String name, String description, BigDecimal basePrice,
        ProductStatus status, Set<UUID> categoryIds) {
    public UpdateProduct {
        if (categoryIds != null) {
            categoryIds = Set.copyOf(categoryIds);
        }
    }
}
