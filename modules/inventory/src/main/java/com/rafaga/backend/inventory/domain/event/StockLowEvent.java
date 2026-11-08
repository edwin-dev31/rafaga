package com.rafaga.backend.inventory.domain.event;

import java.util.UUID;

public record StockLowEvent(UUID productId, UUID variantId, String tenantId,
        int currentStock, int reorderLevel) { }
