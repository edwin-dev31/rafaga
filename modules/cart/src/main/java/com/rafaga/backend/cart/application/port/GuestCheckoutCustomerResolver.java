package com.rafaga.backend.cart.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves or creates a tenant-scoped customer when optional contact details
 * are provided during storefront pre-WhatsApp checkout.
 */
public interface GuestCheckoutCustomerResolver {
    Optional<UUID> resolveOrCreateCustomer(String fullName, String email, String phone);
}
