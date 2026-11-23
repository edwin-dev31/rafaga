package com.rafaga.backend.inventory.application.service;

import java.util.List;
import java.util.UUID;

/**
 * Aggregated stock availability for a product, distinguishing base stock from variant stock.
 * When active variants exist, totalSellable reflects the sum of variant availability so a
 * zero base stock level does not falsely mark the product as out of stock.
 */
public record ProductStockSummaryView(
        UUID productId,
        int baseAvailable,
        int variantsAvailable,
        int totalSellable,
        boolean hasVariants,
        boolean inStock,
        List<VariantStockView> variants) {

    public record VariantStockView(
            UUID variantId,
            int totalQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean inStock) {
    }
}
