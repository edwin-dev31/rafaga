package com.rafaga.backend.catalog.application.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CreateProduct(String sku, String name, String description, BigDecimal basePrice,
        Set<UUID> categoryIds, List<Variant> variants) {
    public CreateProduct {
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(basePrice, "basePrice must not be null");
        categoryIds = Set.copyOf(Objects.requireNonNull(categoryIds, "categoryIds must not be null"));
        variants = List.copyOf(Objects.requireNonNull(variants, "variants must not be null"));
    }

    public record Variant(String sku, String name, Map<String, String> attributes,
            BigDecimal priceAdjustment) {
        public Variant {
            Objects.requireNonNull(sku, "sku must not be null");
            Objects.requireNonNull(name, "name must not be null");
            attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
            Objects.requireNonNull(priceAdjustment, "priceAdjustment must not be null");
        }
    }
}
