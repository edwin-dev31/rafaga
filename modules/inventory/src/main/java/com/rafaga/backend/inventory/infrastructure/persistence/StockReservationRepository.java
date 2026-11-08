package com.rafaga.backend.inventory.infrastructure.persistence;

import com.rafaga.backend.inventory.domain.model.ReservationStatus;
import com.rafaga.backend.inventory.domain.model.StockReservation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockReservation> findByTenantIdAndId(String tenantId, UUID id);

    List<StockReservation> findByTenantIdAndReferenceIdAndStatus(
            String tenantId, String referenceId, ReservationStatus status);

    List<StockReservation> findByTenantIdAndReferenceId(String tenantId, String referenceId);

    List<StockReservation> findByTenantIdAndStatusAndExpiresAtBefore(
            String tenantId, ReservationStatus status, Instant now);
}
