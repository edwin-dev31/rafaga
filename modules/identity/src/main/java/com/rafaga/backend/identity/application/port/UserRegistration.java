package com.rafaga.backend.identity.application.port;

import java.util.Objects;

public record UserRegistration(String username, String firstName, String lastName, String email, String password) {
    public UserRegistration {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
