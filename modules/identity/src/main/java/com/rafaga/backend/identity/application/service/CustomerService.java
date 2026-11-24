package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import com.rafaga.backend.identity.application.port.CustomerStore;
import com.rafaga.backend.identity.domain.model.Customer;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerStore customers;
    private final Clock clock;

    @Autowired
    public CustomerService(CustomerStore customers) {
        this(customers, Clock.systemUTC());
    }

    CustomerService(CustomerStore customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CustomerView> searchCustomers(String query, Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        return customers.search(tenantId, query, pageable).map(CustomerView::from);
    }

    @Transactional(readOnly = true)
    public CustomerView getCustomer(UUID customerId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Customer customer = customers.findById(tenantId, customerId)
                .orElseThrow(() -> new IdentityResourceNotFoundException("Customer", customerId));
        return CustomerView.from(customer);
    }

    @Transactional
    public CustomerView createCustomer(String fullName, String email, String phone, String notes) {
        String tenantId = TenantContext.getCurrentTenantId();
        String normalizedEmail = Customer.normalizeEmail(email);
        String normalizedPhone = Customer.normalizeOptional(phone);
        ensureUniqueContact(tenantId, null, normalizedEmail, normalizedPhone);

        Customer customer = new Customer(tenantId, fullName, normalizedEmail, normalizedPhone, notes, clock.instant());
        return CustomerView.from(customers.save(customer));
    }

    @Transactional
    public CustomerView updateCustomer(UUID customerId, String fullName, String email, String phone, String notes) {
        String tenantId = TenantContext.getCurrentTenantId();
        Customer customer = customers.findById(tenantId, customerId)
                .orElseThrow(() -> new IdentityResourceNotFoundException("Customer", customerId));
        String normalizedEmail = Customer.normalizeEmail(email);
        String normalizedPhone = Customer.normalizeOptional(phone);
        ensureUniqueContact(tenantId, customerId, normalizedEmail, normalizedPhone);

        customer.updateDetails(fullName, normalizedEmail, normalizedPhone, notes, clock.instant());
        return CustomerView.from(customers.save(customer));
    }

    /**
     * Resolves or creates a tenant-scoped customer during optional pre-WhatsApp storefront checkout.
     * When no name is supplied, returns {@link Optional#empty()} without error so guest checkout degrades gracefully.
     */
    @Transactional
    public Optional<CustomerView> findOrCreateCheckoutCustomer(String fullName, String email, String phone) {
        String normalizedName = Customer.normalizeOptional(fullName);
        if (normalizedName == null) {
            return Optional.empty();
        }
        String tenantId = TenantContext.getCurrentTenantId();
        String normalizedEmail = Customer.normalizeEmail(email);
        String normalizedPhone = Customer.normalizeOptional(phone);
        Instant now = clock.instant();

        Optional<Customer> existing = Optional.empty();
        if (normalizedEmail != null) {
            existing = customers.findByEmail(tenantId, normalizedEmail);
        }
        if (existing.isEmpty() && normalizedPhone != null) {
            existing = customers.findByPhone(tenantId, normalizedPhone);
        }
        if (existing.isPresent()) {
            Customer customer = existing.get();
            customer.mergeMissingContact(normalizedName, normalizedEmail, normalizedPhone, now);
            return Optional.of(CustomerView.from(customers.save(customer)));
        }

        Customer created = new Customer(tenantId, normalizedName, normalizedEmail, normalizedPhone, null, now);
        return Optional.of(CustomerView.from(customers.save(created)));
    }

    private void ensureUniqueContact(String tenantId, UUID currentId, String normalizedEmail, String normalizedPhone) {
        if (normalizedEmail != null) {
            customers.findByEmail(tenantId, normalizedEmail)
                    .filter(existing -> !existing.getId().equals(currentId))
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException("Customer with email '" + normalizedEmail + "' already exists");
                    });
        }
        if (normalizedPhone != null) {
            customers.findByPhone(tenantId, normalizedPhone)
                    .filter(existing -> !existing.getId().equals(currentId))
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException("Customer with phone '" + normalizedPhone + "' already exists");
                    });
        }
    }
}
