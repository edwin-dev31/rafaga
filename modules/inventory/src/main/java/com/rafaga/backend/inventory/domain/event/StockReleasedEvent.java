package com.rafaga.backend.inventory.domain.event;

import java.util.UUID;

public record StockReleasedEvent(UUID reservationId, UUID productId, UUID variantId,
        String tenantId, int quantity) { }
