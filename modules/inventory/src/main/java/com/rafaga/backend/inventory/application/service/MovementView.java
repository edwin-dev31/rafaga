package com.rafaga.backend.inventory.application.service;

import com.rafaga.backend.inventory.domain.model.InventoryMovement;
import com.rafaga.backend.inventory.domain.model.MovementType;
import java.time.Instant;
import java.util.UUID;

public record MovementView(UUID id, UUID productId, UUID variantId, MovementType type,
        int quantity, int previousTotal, int newTotal, String referenceId, String notes,
        Instant timestamp) {
    public static MovementView from(InventoryMovement movement) {
        return new MovementView(movement.getId(), movement.getProductId(), movement.getVariantId(),
                movement.getType(), movement.getQuantity(), movement.getPreviousTotal(),
                movement.getNewTotal(), movement.getReferenceId(), movement.getNotes(),
                movement.getTimestamp());
    }
}
