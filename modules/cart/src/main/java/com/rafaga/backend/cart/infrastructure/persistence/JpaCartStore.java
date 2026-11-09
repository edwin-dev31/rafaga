package com.rafaga.backend.cart.infrastructure.persistence;

import com.rafaga.backend.cart.application.port.CartStore;
import com.rafaga.backend.cart.domain.model.Cart;
import com.rafaga.backend.cart.domain.model.CartStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaCartStore implements CartStore {

    private final CartRepository carts;

    JpaCartStore(CartRepository carts) { this.carts = carts; }

    @Override
    public Cart save(Cart cart) { return carts.saveAndFlush(cart); }

    @Override
    public Optional<Cart> find(UUID cartId, String tenantId) {
        return carts.findByIdAndTenantId(cartId, tenantId);
    }

    @Override
    public Optional<Cart> findActiveByCustomer(String tenantId, UUID customerId) {
        return carts.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, CartStatus.ACTIVE);
    }
}
