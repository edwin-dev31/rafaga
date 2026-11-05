package com.rafaga.backend.identity.application.exception;

import java.util.UUID;

public class IdentityResourceNotFoundException extends RuntimeException {
    public IdentityResourceNotFoundException(String resourceType, UUID resourceId) {
        super(resourceType + " not found: " + resourceId);
    }

    public IdentityResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType + " not found: " + identifier);
    }
}
