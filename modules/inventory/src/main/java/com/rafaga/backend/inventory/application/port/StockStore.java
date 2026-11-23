package com.rafaga.backend.inventory.application.port;

import com.rafaga.backend.inventory.domain.model.InventoryMovement;
import com.rafaga.backend.inventory.domain.model.StockLevel;
import com.rafaga.backend.inventory.domain.model.StockReservation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockStore {
    Optional<StockLevel> findLevel(String tenantId, UUID productId, UUID variantId);
    default List<StockLevel> findLevelsForProducts(String tenantId, Collection<UUID> productIds) {
        return List.of();
    }
    Optional<StockLevel> lockLevel(String tenantId, UUID productId, UUID variantId);
    StockLevel saveLevel(StockLevel level);
    InventoryMovement saveMovement(InventoryMovement movement);
    List<InventoryMovement> findMovements(String tenantId, UUID productId, UUID variantId);
    StockReservation saveReservation(StockReservation reservation);
    Optional<StockReservation> lockReservation(String tenantId, UUID reservationId);
    List<StockReservation> findByReference(String tenantId, String referenceId);
    List<StockReservation> findActiveByReference(String tenantId, String referenceId);
    List<StockReservation> findExpired(String tenantId, Instant now);
}
