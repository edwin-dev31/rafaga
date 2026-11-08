package com.rafaga.backend.catalog.application.service;

import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.ProductVariant;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ProductView(UUID id, String sku, String name, String description,
        BigDecimal basePrice, ProductStatus status, Set<UUID> categoryIds,
        List<VariantView> variants, Instant createdAt, Instant updatedAt) {
    public static ProductView from(Product product) {
        return new ProductView(
                product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getBasePrice(), product.getStatus(),
                product.getCategories().stream().map(category -> category.getId()).collect(Collectors.toSet()),
                product.getVariants().stream().map(VariantView::from).toList(),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    public record VariantView(UUID id, String sku, String name, Map<String, String> attributes,
            BigDecimal priceAdjustment, VariantStatus status) {
        static VariantView from(ProductVariant variant) {
            return new VariantView(variant.getId(), variant.getSku(), variant.getName(),
                    variant.getAttributes(), variant.getPriceAdjustment(), variant.getStatus());
        }
    }
}
