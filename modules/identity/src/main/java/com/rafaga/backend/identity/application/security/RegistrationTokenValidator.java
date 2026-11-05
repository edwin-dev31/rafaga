package com.rafaga.backend.identity.application.security;

import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class RegistrationTokenValidator {
    private final RegistrationProperties properties;

    public RegistrationTokenValidator(RegistrationProperties properties) {
        this.properties = properties;
    }

    public void validate(String providedToken) {
        byte[] expected = configuredToken().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken == null ? new byte[0] : providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new InvalidRegistrationTokenException();
        }
    }

    private String configuredToken() {
        String token = properties.getRegistrationToken();
        if (token == null || token.isBlank()) {
            throw new InvalidRegistrationTokenException();
        }
        return token;
    }
}
