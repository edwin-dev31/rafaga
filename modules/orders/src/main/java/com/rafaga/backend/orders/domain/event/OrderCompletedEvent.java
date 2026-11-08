package com.rafaga.backend.orders.domain.event;

import java.util.UUID;

public record OrderCompletedEvent(UUID orderId, String tenantId) { }
