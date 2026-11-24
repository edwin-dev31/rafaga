package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.identity.application.exception.CrossTenantAccessException;
import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.rafaga.backend.identity.application.exception.UnauthenticatedUserException;
import com.rafaga.backend.identity.application.exception.UserAlreadyExistsException;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        AuthenticationController.class,
        UserController.class,
        TenantUserAdminController.class,
        CustomerController.class
})
class IdentityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(IdentityExceptionHandler.class);

    @ExceptionHandler({
            CrossTenantAccessException.class,
            IdentityResourceNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError resourceNotFound(RuntimeException exception) {
        return ApiError.of("RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(UnauthenticatedUserException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError unauthenticatedUser(UnauthenticatedUserException exception) {
        return ApiError.of("UNAUTHENTICATED", exception.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationTokenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ApiError invalidRegistrationToken(InvalidRegistrationTokenException exception) {
        return ApiError.of("FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError invalidCredentials(InvalidCredentialsException exception) {
        return ApiError.of("INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler({PasswordPolicyViolationException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError passwordPolicyViolation(RuntimeException exception) {
        return ApiError.of("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ApiError.validation("Request validation failed", fields);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError userAlreadyExists(UserAlreadyExistsException exception) {
        return ApiError.of("CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(TenantLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError planLimitExceeded(TenantLimitExceededException exception) {
        return ApiError.of("PLAN_LIMIT_EXCEEDED", exception.getMessage());
    }

    @ExceptionHandler(IdentityProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiError identityProviderUnavailable(IdentityProviderUnavailableException exception) {
        return ApiError.of("IDENTITY_PROVIDER_UNAVAILABLE", "Authentication service is temporarily unavailable");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiError unexpectedError(RuntimeException exception) {
        log.error("Unexpected error in identity module: {}", exception.getMessage(), exception);
        return ApiError.of("INTERNAL_ERROR", "An internal error occurred while processing your request");
    }
}
