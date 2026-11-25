package com.rafaga.backend.catalog.infrastructure.web;

import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.infrastructure.web.storefront.StorefrontCatalogController;
import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ProductController.class, CategoryController.class, StorefrontCatalogController.class})
class CatalogExceptionHandler {
    @ExceptionHandler(CatalogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(CatalogNotFoundException exception) {
        return ApiError.of("RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CatalogConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError conflict(CatalogConflictException exception) {
        return ApiError.of("CATALOG_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError dataConflict(DataIntegrityViolationException exception) {
        return ApiError.of("DATA_CONFLICT", "Catalog resource conflicts with existing data");
    }

    @ExceptionHandler(TenantLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError planLimitExceeded(TenantLimitExceededException exception) {
        return ApiError.of("PLAN_LIMIT_EXCEEDED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidRequest(IllegalArgumentException exception) {
        return ApiError.of("INVALID_ARGUMENT", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ApiError.validation("Request validation failed", fields);
    }
}
