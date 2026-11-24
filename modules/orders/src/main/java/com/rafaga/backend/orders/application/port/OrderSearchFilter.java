package com.rafaga.backend.orders.application.port;

import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderSearchFilter(OrderStatus status, UUID customerId, Instant from, Instant to) {
    public static OrderSearchFilter empty() {
        return new OrderSearchFilter(null, null, null, null);
    }

    public boolean isEmpty() {
        return status == null && customerId == null && from == null && to == null;
    }
}
