package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.rafaga.backend.identity.application.exception.UnauthenticatedUserException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthenticationController.class, UserController.class})
class IdentityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(IdentityExceptionHandler.class);

    @ExceptionHandler({CrossTenantAccessException.class, IdentityResourceNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> resourceNotFound(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(UnauthenticatedUserException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> unauthenticatedUser(UnauthenticatedUserException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationTokenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> invalidRegistrationToken(InvalidRegistrationTokenException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> invalidCredentials(InvalidCredentialsException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> passwordPolicyViolation(PasswordPolicyViolationException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> validationFailed(MethodArgumentNotValidException exception) {
        return Map.of("message", "Request validation failed");
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> userAlreadyExists(UserAlreadyExistsException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(IdentityProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, String> identityProviderUnavailable(IdentityProviderUnavailableException exception) {
        return Map.of("message", "Authentication service is temporarily unavailable");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Map<String, String> unexpectedError(RuntimeException exception) {
        log.error("Unexpected error in identity module: {}", exception.getMessage(), exception);
        return Map.of("message", "An internal error occurred while processing your request");
    }
}
