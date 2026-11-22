package com.rafaga.backend.identity.application.exception;

public class IdentityProviderUnavailableException extends RuntimeException {
    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }
}
