package com.rafaga.backend.inventory.application.service;

import com.rafaga.backend.inventory.domain.model.StockLevel;
import java.time.Instant;
import java.util.UUID;

public record StockLevelView(UUID id, UUID productId, UUID variantId, int totalQuantity,
        int availableQuantity, int reservedQuantity, int reorderLevel, int reorderQuantity,
        Instant lastUpdated) {
    public static StockLevelView from(StockLevel level) {
        return new StockLevelView(level.getId(), level.getProductId(), level.getVariantId(),
                level.getTotalQuantity(), level.getAvailableQuantity(), level.getReservedQuantity(),
                level.getReorderLevel(), level.getReorderQuantity(), level.getLastUpdated());
    }
}
