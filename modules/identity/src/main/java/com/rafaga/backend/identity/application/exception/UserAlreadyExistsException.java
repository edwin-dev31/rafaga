package com.rafaga.backend.identity.application.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException() {
        super("A user with the supplied identity already exists");
    }
}
