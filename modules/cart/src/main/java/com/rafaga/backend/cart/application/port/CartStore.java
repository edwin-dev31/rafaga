package com.rafaga.backend.cart.application.port;

import com.rafaga.backend.cart.domain.model.Cart;
import java.util.Optional;
import java.util.UUID;

public interface CartStore {
    Cart save(Cart cart);
    Optional<Cart> find(UUID cartId, String tenantId);
    /** Returns the single ACTIVE cart for a customer, if any. */
    Optional<Cart> findActiveByCustomer(String tenantId, UUID customerId);
}
