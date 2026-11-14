package com.rafaga.backend.tenant.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class FeatureDisabledException extends RuntimeException {
    public FeatureDisabledException(String featureName) {
        super("Feature is not enabled for this tenant: " + featureName);
    }
}
