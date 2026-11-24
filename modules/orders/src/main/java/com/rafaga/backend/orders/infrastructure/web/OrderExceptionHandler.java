package com.rafaga.backend.orders.infrastructure.web;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import com.rafaga.backend.orders.application.exception.OrderConflictException;
import com.rafaga.backend.orders.application.exception.OrderNotFoundException;
import com.rafaga.backend.orders.domain.exception.InvalidOrderTransitionException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {
    @ExceptionHandler({OrderNotFoundException.class, CatalogNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({OrderConflictException.class, InvalidOrderTransitionException.class,
            InsufficientStockException.class, StockNotFoundException.class})
    ResponseEntity<ApiError> conflict(RuntimeException exception) {
        String code = exception instanceof InsufficientStockException
                ? "INSUFFICIENT_STOCK"
                : "ORDER_CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(code, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
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
