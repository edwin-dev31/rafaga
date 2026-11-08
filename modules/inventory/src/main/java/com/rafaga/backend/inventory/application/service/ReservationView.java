package com.rafaga.backend.inventory.application.service;

import com.rafaga.backend.inventory.domain.model.ReservationStatus;
import com.rafaga.backend.inventory.domain.model.StockReservation;
import java.time.Instant;
import java.util.UUID;

public record ReservationView(UUID id, UUID productId, UUID variantId, int quantity,
        String referenceId, ReservationStatus status, Instant createdAt, Instant expiresAt) {
    public static ReservationView from(StockReservation reservation) {
        return new ReservationView(reservation.getId(), reservation.getProductId(),
                reservation.getVariantId(), reservation.getQuantity(), reservation.getReferenceId(),
                reservation.getStatus(), reservation.getCreatedAt(), reservation.getExpiresAt());
    }
}
