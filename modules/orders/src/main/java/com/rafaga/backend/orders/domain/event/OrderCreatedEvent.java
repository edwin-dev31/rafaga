package com.rafaga.backend.orders.domain.event;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, String tenantId, UUID customerId) { }
