package com.rafaga.backend.inventory.infrastructure.persistence;

import com.rafaga.backend.inventory.domain.model.StockLevel;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {
    Optional<StockLevel> findByTenantIdAndProductIdAndVariantId(String tenantId, UUID productId, UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockLevel> findLockedByTenantIdAndProductIdAndVariantId(
            String tenantId, UUID productId, UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockLevel> findLockedByTenantIdAndProductIdAndVariantIdIsNull(String tenantId, UUID productId);
}
