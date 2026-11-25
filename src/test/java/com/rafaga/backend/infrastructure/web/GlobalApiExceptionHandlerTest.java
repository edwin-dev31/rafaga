package com.rafaga.backend.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.ApiError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

class GlobalApiExceptionHandlerTest {
    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.remove(ApiError.CORRELATION_MDC_KEY);
    }

    @Test
    void formatsAccessDeniedResponseStatusAndDomainErrorsAsApiErrorWithTraceId() {
        MDC.put(ApiError.CORRELATION_MDC_KEY, "trace-global-1");

        var forbidden = handler.accessDenied(new AccessDeniedException("Denied"));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).isNotNull();
        assertThat(forbidden.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(forbidden.getBody().traceId()).isEqualTo("trace-global-1");

        var notFound = handler.responseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().message()).isEqualTo("Tenant not found");
        assertThat(notFound.getBody().traceId()).isEqualTo("trace-global-1");

        var badRequest = handler.badRequest(new IllegalArgumentException("Bad input"));
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getBody()).isNotNull();
        assertThat(badRequest.getBody().code()).isEqualTo("INVALID_ARGUMENT");

        var conflict = handler.conflict(new IllegalStateException("Conflict"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).isNotNull();
        assertThat(conflict.getBody().code()).isEqualTo("CONFLICT");
    }
}
