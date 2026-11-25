package com.rafaga.backend.catalog.application.exception;

import java.util.UUID;

public class CatalogNotFoundException extends RuntimeException {
    public CatalogNotFoundException(String resource, UUID id) {
        super(resource + " not found: " + id);
    }

    public CatalogNotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier);
    }
}
