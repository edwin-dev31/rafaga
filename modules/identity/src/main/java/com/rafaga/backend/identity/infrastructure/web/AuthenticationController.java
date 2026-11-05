package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.service.UserProfile;
import com.rafaga.backend.identity.application.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/auth")
public class AuthenticationController {
    private final UserService userService;

    public AuthenticationController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(userService.authenticateUser(request.username(), request.password()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @RequestHeader(name = "X-Registration-Token", required = false) String registrationToken,
            @Valid @RequestBody RegistrationRequest request) {
        return UserResponse.from(userService.registerUser(
                registrationToken,
                new UserRegistration(
                        request.username(),
                        request.firstName(),
                        request.lastName(),
                        request.email(),
                        request.password())));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegistrationRequest(
            @NotBlank String username,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
        static TokenResponse from(AuthenticationTokens tokens) {
            return new TokenResponse(
                    tokens.accessToken(),
                    tokens.refreshToken(),
                    tokens.tokenType(),
                    tokens.expiresInSeconds());
        }
    }

    public record UserResponse(String id, String username, String email) {
        static UserResponse from(UserProfile user) {
            return new UserResponse(user.id().toString(), user.username(), user.email());
        }
    }
}
