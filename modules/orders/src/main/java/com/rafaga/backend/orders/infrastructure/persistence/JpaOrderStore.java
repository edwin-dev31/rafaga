package com.rafaga.backend.orders.infrastructure.persistence;

import com.rafaga.backend.orders.application.port.OrderSearchFilter;
import com.rafaga.backend.orders.application.port.OrderStore;
import com.rafaga.backend.orders.domain.model.Order;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    @Override
    public Page<Order> findAll(String tenantId, OrderSearchFilter filter, Pageable pageable) {
        if (filter == null || filter.isEmpty()) {
            return orders.findAllByTenantId(tenantId, pageable);
        }
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return orders.findAll(spec, pageable);
    }

    public List<Order> findByCustomer(String tenantId, UUID customerId) {
        return orders.findByTenantIdAndCustomerId(tenantId, customerId);
    }

    public List<Order> findByStatus(String tenantId, OrderStatus status) {
        return orders.findByTenantIdAndStatus(tenantId, status);
    }
}
