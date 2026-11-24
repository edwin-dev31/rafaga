package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CustomerJpaRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByTenantIdAndId(String tenantId, UUID id);
    Optional<Customer> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);
    Optional<Customer> findByTenantIdAndPhone(String tenantId, String phone);
}
