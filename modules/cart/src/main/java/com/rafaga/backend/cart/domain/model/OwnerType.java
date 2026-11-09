package com.rafaga.backend.cart.domain.model;

/** Distinguishes between a customer-owned cart and a guest-session-owned cart. */
public enum OwnerType {
    CUSTOMER,
    GUEST
}
