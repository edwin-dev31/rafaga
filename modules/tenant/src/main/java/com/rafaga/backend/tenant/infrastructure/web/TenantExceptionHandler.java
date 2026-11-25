package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        TenantAdminController.class,
        TenantLimitsAdminController.class,
        TenantFeaturesAdminController.class,
        TenantRoutingAdminController.class,
        R2CorsAdminController.class,
        DeploymentTargetAdminController.class,
        TenantTargetTransitionAdminController.class
})
class TenantExceptionHandler {
    @ExceptionHandler(TenantNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError tenantNotFound(TenantNotFoundException exception) {
        return ApiError.of("RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(TenantLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError limitExceeded(TenantLimitExceededException exception) {
        return ApiError.of("PLAN_LIMIT_EXCEEDED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError badRequest(IllegalArgumentException exception) {
        return ApiError.of("INVALID_ARGUMENT", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError conflict(IllegalStateException exception) {
        return ApiError.of("TENANT_CONFLICT", exception.getMessage());
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
