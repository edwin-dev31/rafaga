package com.rafaga.backend.cart.application.exception;

import java.util.UUID;

public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(UUID cartId) {
        super("Cart not found: " + cartId);
    }

    public CartNotFoundException(String message) {
        super(message);
    }
}
