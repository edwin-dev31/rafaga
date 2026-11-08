package com.rafaga.backend.orders.infrastructure.persistence;

import com.rafaga.backend.orders.application.port.OrderStore;
import com.rafaga.backend.orders.domain.model.Order;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaOrderStore implements OrderStore {
    private final OrderRepository orders;

    JpaOrderStore(OrderRepository orders) { this.orders = orders; }

    public Order save(Order order) { return orders.saveAndFlush(order); }

    public Optional<Order> find(UUID orderId, String tenantId) {
        return orders.findByIdAndTenantId(orderId, tenantId);
    }

    public Page<Order> findAll(String tenantId, Pageable pageable) {
        return orders.findAllByTenantId(tenantId, pageable);
    }

    public List<Order> findByCustomer(String tenantId, UUID customerId) {
        return orders.findByTenantIdAndCustomerId(tenantId, customerId);
    }

    public List<Order> findByStatus(String tenantId, OrderStatus status) {
        return orders.findByTenantIdAndStatus(tenantId, status);
    }
}
