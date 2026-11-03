package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TenantAdminController.class)
class TenantExceptionHandler {
    @ExceptionHandler(TenantNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> tenantNotFound(TenantNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }
}
