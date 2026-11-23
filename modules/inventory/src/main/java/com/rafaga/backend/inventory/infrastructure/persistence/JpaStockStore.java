package com.rafaga.backend.inventory.infrastructure.persistence;

import com.rafaga.backend.inventory.application.port.StockStore;
import com.rafaga.backend.inventory.domain.model.InventoryMovement;
import com.rafaga.backend.inventory.domain.model.ReservationStatus;
import com.rafaga.backend.inventory.domain.model.StockLevel;
import com.rafaga.backend.inventory.domain.model.StockReservation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaStockStore implements StockStore {
    private final StockLevelRepository levels;
    private final InventoryMovementRepository movements;
    private final StockReservationRepository reservations;

    JpaStockStore(StockLevelRepository levels, InventoryMovementRepository movements,
            StockReservationRepository reservations) {
        this.levels = levels;
        this.movements = movements;
        this.reservations = reservations;
    }

    public Optional<StockLevel> findLevel(String tenantId, UUID productId, UUID variantId) {
        return levels.findByTenantIdAndProductIdAndVariantId(tenantId, productId, variantId);
    }

    @Override
    public List<StockLevel> findLevelsForProducts(String tenantId, Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return levels.findByTenantIdAndProductIdIn(tenantId, productIds);
    }

    public Optional<StockLevel> lockLevel(String tenantId, UUID productId, UUID variantId) {
        return variantId == null
                ? levels.findLockedByTenantIdAndProductIdAndVariantIdIsNull(tenantId, productId)
                : levels.findLockedByTenantIdAndProductIdAndVariantId(tenantId, productId, variantId);
    }

    public StockLevel saveLevel(StockLevel level) { return levels.saveAndFlush(level); }
    public InventoryMovement saveMovement(InventoryMovement movement) { return movements.saveAndFlush(movement); }

    public List<InventoryMovement> findMovements(String tenantId, UUID productId, UUID variantId) {
        return movements.findByTenantIdAndProductIdAndVariantIdOrderByTimestampDesc(tenantId, productId, variantId);
    }

    public StockReservation saveReservation(StockReservation reservation) {
        return reservations.saveAndFlush(reservation);
    }

    public Optional<StockReservation> lockReservation(String tenantId, UUID reservationId) {
        return reservations.findByTenantIdAndId(tenantId, reservationId);
    }

    public List<StockReservation> findByReference(String tenantId, String referenceId) {
        return reservations.findByTenantIdAndReferenceId(tenantId, referenceId);
    }

    public List<StockReservation> findActiveByReference(String tenantId, String referenceId) {
        return reservations.findByTenantIdAndReferenceIdAndStatus(tenantId, referenceId, ReservationStatus.ACTIVE);
    }

    public List<StockReservation> findExpired(String tenantId, Instant now) {
        return reservations.findByTenantIdAndStatusAndExpiresAtBefore(tenantId, ReservationStatus.ACTIVE, now);
    }
}
