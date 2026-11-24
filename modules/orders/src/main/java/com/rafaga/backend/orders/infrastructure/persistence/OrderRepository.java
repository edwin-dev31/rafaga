package com.rafaga.backend.orders.infrastructure.persistence;

import com.rafaga.backend.orders.domain.model.Order;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByIdAndTenantId(UUID id, String tenantId);
    Optional<Order> findByTenantIdAndCartId(String tenantId, UUID cartId);
    Optional<Order> findByTenantIdAndPublicToken(String tenantId, String publicToken);
    Page<Order> findAllByTenantId(String tenantId, Pageable pageable);
    List<Order> findByTenantIdAndCustomerId(String tenantId, UUID customerId);
    List<Order> findByTenantIdAndStatus(String tenantId, OrderStatus status);
}
