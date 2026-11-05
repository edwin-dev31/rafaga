package com.rafaga.backend.identity.application.exception;

public class UnauthenticatedUserException extends RuntimeException {
    public UnauthenticatedUserException() {
        super("No authenticated user is available");
    }
}
