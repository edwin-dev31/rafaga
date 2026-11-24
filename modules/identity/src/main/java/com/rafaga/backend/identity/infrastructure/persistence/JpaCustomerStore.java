package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.application.port.CustomerStore;
import com.rafaga.backend.identity.domain.model.Customer;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class JpaCustomerStore implements CustomerStore {
    private final CustomerJpaRepository repository;

    JpaCustomerStore(CustomerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Customer save(Customer customer) {
        return repository.saveAndFlush(customer);
    }

    @Override
    public Optional<Customer> findById(String tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public Optional<Customer> findByEmail(String tenantId, String email) {
        return repository.findByTenantIdAndEmailIgnoreCase(tenantId, email);
    }

    @Override
    public Optional<Customer> findByPhone(String tenantId, String phone) {
        return repository.findByTenantIdAndPhone(tenantId, phone);
    }

    @Override
    public Page<Customer> search(String tenantId, String query, Pageable pageable) {
        Specification<Customer> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate byName = cb.like(cb.lower(root.get("fullName")), pattern);
                Predicate byEmail = cb.like(cb.lower(root.get("email")), pattern);
                Predicate byPhone = cb.like(cb.lower(root.get("phone")), pattern);
                predicates.add(cb.or(byName, byEmail, byPhone));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }
}
