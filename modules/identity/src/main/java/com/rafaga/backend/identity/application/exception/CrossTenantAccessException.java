package com.rafaga.backend.identity.application.exception;

public class CrossTenantAccessException extends RuntimeException {
    public CrossTenantAccessException() {
        super("The requested identity resource does not exist");
    }
}
