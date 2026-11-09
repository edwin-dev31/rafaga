package com.rafaga.backend.cart.application.exception;

public class CartConflictException extends RuntimeException {
    public CartConflictException(String message) {
        super(message);
    }
}
