package com.rafaga.backend.inventory.application.exception;

public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(String message) { super(message); }
}
