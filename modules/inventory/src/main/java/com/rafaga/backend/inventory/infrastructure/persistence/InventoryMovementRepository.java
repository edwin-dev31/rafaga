package com.rafaga.backend.inventory.infrastructure.persistence;

import com.rafaga.backend.inventory.domain.model.InventoryMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    List<InventoryMovement> findByTenantIdAndProductIdAndVariantIdOrderByTimestampDesc(
            String tenantId, UUID productId, UUID variantId);
}
