package com.rafaga.backend.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import com.rafaga.backend.identity.application.port.CustomerStore;
import com.rafaga.backend.identity.domain.model.Customer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CustomerServiceTest {
    private static final String TENANT = "tenant-a";
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private CustomerStore store;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        store = mock(CustomerStore.class);
        service = new CustomerService(store, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsAndSearchesCustomersScopedToTenant() {
        when(store.findByEmail(TENANT, "ana@example.com")).thenReturn(Optional.empty());
        when(store.findByPhone(TENANT, "3001234567")).thenReturn(Optional.empty());
        when(store.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerView created = service.createCustomer("  Ana Pérez ", "ANA@Example.com", "3001234567", "WhatsApp");

        assertThat(created.fullName()).isEqualTo("Ana Pérez");
        assertThat(created.email()).isEqualTo("ana@example.com");
        assertThat(created.phone()).isEqualTo("3001234567");

        Customer entity = new Customer(TENANT, "Ana Pérez", "ana@example.com", "3001234567", "WhatsApp", NOW);
        when(store.search(TENANT, "ana", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        var page = service.searchCustomers("ana", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().fullName()).isEqualTo("Ana Pérez");
    }

    @Test
    void rejectsDuplicateEmailWithinSameTenant() {
        Customer existing = new Customer(TENANT, "Ana", "ana@example.com", null, null, NOW);
        when(store.findByEmail(TENANT, "ana@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createCustomer("Otra Ana", "ana@example.com", null, null))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void throwsNotFoundForMissingOrForeignTenantCustomer() {
        UUID id = UUID.randomUUID();
        when(store.findById(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomer(id))
                .isInstanceOf(IdentityResourceNotFoundException.class);
    }

    @Test
    void checkoutCustomerDegradesGracefullyWhenNameIsMissing() {
        Optional<CustomerView> result = service.findOrCreateCheckoutCustomer("   ", "ana@example.com", "3001234567");

        assertThat(result).isEmpty();
        verify(store, never()).save(any());
    }

    @Test
    void checkoutCustomerReusesExistingCustomerByPhone() {
        Customer existing = new Customer(TENANT, "Ana Pérez", null, "3001234567", null, NOW);
        when(store.findByPhone(TENANT, "3001234567")).thenReturn(Optional.of(existing));
        when(store.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<CustomerView> result = service.findOrCreateCheckoutCustomer("Ana Pérez", "ana@example.com", "3001234567");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(existing.getId());
        assertThat(result.get().email()).isEqualTo("ana@example.com");
    }
}
