package com.rafaga.backend.cart.infrastructure.web;

import com.rafaga.backend.cart.application.exception.CartConflictException;
import com.rafaga.backend.cart.application.exception.CartNotFoundException;
import com.rafaga.backend.cart.domain.exception.InvalidCartOperationException;
import com.rafaga.backend.cart.infrastructure.web.storefront.StorefrontCartController;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.common.application.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {CartController.class, StorefrontCartController.class})
public class CartExceptionHandler {

    @ExceptionHandler({CartNotFoundException.class, CatalogNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({CartConflictException.class, InvalidCartOperationException.class})
    ResponseEntity<ApiError> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CART_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DATA_CONFLICT", "Cart conflicts with existing data"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_ARGUMENT", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", fields));
    }
}
