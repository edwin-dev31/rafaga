package com.rafaga.backend.orders.infrastructure.web;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import com.rafaga.backend.orders.application.exception.OrderConflictException;
import com.rafaga.backend.orders.application.exception.OrderNotFoundException;
import com.rafaga.backend.orders.domain.exception.InvalidOrderTransitionException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {
    @ExceptionHandler({OrderNotFoundException.class, CatalogNotFoundException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({OrderConflictException.class, InvalidOrderTransitionException.class,
            InsufficientStockException.class, StockNotFoundException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
