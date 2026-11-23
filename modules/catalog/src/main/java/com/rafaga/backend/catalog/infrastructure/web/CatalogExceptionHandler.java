package com.rafaga.backend.catalog.infrastructure.web;

import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.infrastructure.web.storefront.StorefrontCatalogController;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ProductController.class, CategoryController.class, StorefrontCatalogController.class})
class CatalogExceptionHandler {
    @ExceptionHandler(CatalogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(CatalogNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler({CatalogConflictException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(RuntimeException exception) {
        return Map.of("message", "Catalog resource conflicts with existing data");
    }

    @ExceptionHandler(TenantLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> planLimitExceeded(TenantLimitExceededException exception) {
        return Map.of("code", "PLAN_LIMIT_EXCEEDED", "message", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }
}
