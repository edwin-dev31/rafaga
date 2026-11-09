package com.rafaga.backend.cart.infrastructure.persistence;

import com.rafaga.backend.cart.domain.model.Cart;
import com.rafaga.backend.cart.domain.model.CartStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByIdAndTenantId(UUID id, String tenantId);
    Optional<Cart> findByTenantIdAndCustomerIdAndStatus(
            String tenantId, UUID customerId, CartStatus status);
}
