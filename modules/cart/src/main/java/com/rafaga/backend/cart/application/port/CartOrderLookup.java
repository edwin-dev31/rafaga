package com.rafaga.backend.cart.application.port;

import java.util.Optional;
import java.util.UUID;

/** Looks up the order created from a checked-out cart in the current tenant. */
public interface CartOrderLookup {
    Optional<OrderReference> findOrderByCartId(UUID cartId);

    record OrderReference(UUID orderId, String publicToken) { }
}
