package com.rafaga.backend.orders.application.port;

import com.rafaga.backend.orders.domain.model.Order;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderStore {
    Order save(Order order);
    Optional<Order> find(UUID orderId, String tenantId);
    Page<Order> findAll(String tenantId, Pageable pageable);
    List<Order> findByCustomer(String tenantId, UUID customerId);
    List<Order> findByStatus(String tenantId, OrderStatus status);
}
