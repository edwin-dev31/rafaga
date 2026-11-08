package com.rafaga.backend.inventory.infrastructure.web;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StockController.class)
public class StockExceptionHandler {
    @ExceptionHandler({StockNotFoundException.class, CatalogNotFoundException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<Map<String, String>> insufficient(InsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
