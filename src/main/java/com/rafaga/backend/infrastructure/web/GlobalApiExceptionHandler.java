package com.rafaga.backend.infrastructure.web;

import com.rafaga.backend.common.application.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fallback exception handler ensuring controllers outside module-specific advices
 * still emit the standard {@link ApiError} envelope with correlation traceId.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalApiExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", fields));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        String code = exception.getStatusCode().toString().replace(' ', '_');
        String message = exception.getReason() != null ? exception.getReason() : exception.getMessage();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiError.of(code, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_ARGUMENT", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", exception.getMessage()));
    }
}
