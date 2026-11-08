package com.rafaga.backend.catalog.application.exception;

public class CatalogConflictException extends RuntimeException {
    public CatalogConflictException(String message) {
        super(message);
    }
}
