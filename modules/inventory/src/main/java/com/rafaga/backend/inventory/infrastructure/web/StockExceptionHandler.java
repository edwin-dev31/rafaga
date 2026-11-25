package com.rafaga.backend.inventory.infrastructure.web;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StockController.class)
public class StockExceptionHandler {
    @ExceptionHandler({StockNotFoundException.class, CatalogNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ApiError> insufficient(InsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("INSUFFICIENT_STOCK", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ApiError> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_ARGUMENT", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", fields));
    }
}
