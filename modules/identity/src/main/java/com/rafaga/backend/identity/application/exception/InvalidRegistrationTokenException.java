package com.rafaga.backend.identity.application.exception;

public class InvalidRegistrationTokenException extends RuntimeException {
    public InvalidRegistrationTokenException() {
        super("The registration token is invalid");
    }
}
