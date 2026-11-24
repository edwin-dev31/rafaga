package com.rafaga.backend.identity.application.port;

import com.rafaga.backend.identity.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerStore {
    Customer save(Customer customer);
    Optional<Customer> findById(String tenantId, UUID id);
    Optional<Customer> findByEmail(String tenantId, String email);
    Optional<Customer> findByPhone(String tenantId, String phone);
    Page<Customer> search(String tenantId, String query, Pageable pageable);
}
