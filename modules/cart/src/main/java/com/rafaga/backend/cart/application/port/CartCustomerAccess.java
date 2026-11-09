package com.rafaga.backend.cart.application.port;

import java.util.UUID;

/** Authorizes access to one customer's cart in the current tenant. */
public interface CartCustomerAccess {
    void requireAccess(UUID customerId);
}
