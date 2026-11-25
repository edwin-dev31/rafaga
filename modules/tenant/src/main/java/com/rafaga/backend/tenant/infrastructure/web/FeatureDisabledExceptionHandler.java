package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.tenant.application.exception.FeatureDisabledException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class FeatureDisabledExceptionHandler {
    @ExceptionHandler(FeatureDisabledException.class)
    ResponseEntity<ApiError> featureDisabled(FeatureDisabledException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FEATURE_DISABLED", exception.getMessage()));
    }
}
