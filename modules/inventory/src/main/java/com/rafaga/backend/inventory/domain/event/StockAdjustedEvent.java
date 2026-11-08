package com.rafaga.backend.inventory.domain.event;

import com.rafaga.backend.inventory.domain.model.MovementType;
import java.util.UUID;

public record StockAdjustedEvent(UUID productId, UUID variantId, String tenantId,
        int oldQuantity, int newQuantity, MovementType type) { }
