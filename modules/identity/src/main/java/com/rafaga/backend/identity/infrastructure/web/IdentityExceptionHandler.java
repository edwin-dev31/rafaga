package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.UnauthenticatedUserException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthenticationController.class, UserController.class})
class IdentityExceptionHandler {
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

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> userAlreadyExists(UserAlreadyExistsException exception) {
        return Map.of("message", exception.getMessage());
    }
}
