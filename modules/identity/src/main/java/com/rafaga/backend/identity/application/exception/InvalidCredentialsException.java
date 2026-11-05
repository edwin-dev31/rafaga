package com.rafaga.backend.identity.application.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("The provided credentials are invalid");
    }
}
