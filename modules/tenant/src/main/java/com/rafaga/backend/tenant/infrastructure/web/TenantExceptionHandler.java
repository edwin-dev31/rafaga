package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
    Map<String, String> tenantNotFound(TenantNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(TenantLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> limitExceeded(TenantLimitExceededException exception) {
        return Map.of("code", "PLAN_LIMIT_EXCEEDED", "message", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(IllegalStateException exception) {
        return Map.of("message", exception.getMessage());
    }
}
