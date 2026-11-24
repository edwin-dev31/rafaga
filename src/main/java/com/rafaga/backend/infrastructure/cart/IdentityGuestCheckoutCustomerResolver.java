package com.rafaga.backend.infrastructure.cart;

import com.rafaga.backend.cart.application.port.GuestCheckoutCustomerResolver;
import com.rafaga.backend.identity.application.service.CustomerService;
import com.rafaga.backend.identity.application.service.CustomerView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityGuestCheckoutCustomerResolver implements GuestCheckoutCustomerResolver {
    private final CustomerService customers;

    public IdentityGuestCheckoutCustomerResolver(CustomerService customers) {
        this.customers = customers;
    }

    @Override
    public Optional<UUID> resolveOrCreateCustomer(String fullName, String email, String phone) {
        return customers.findOrCreateCheckoutCustomer(fullName, email, phone)
                .map(CustomerView::id);
    }
}
