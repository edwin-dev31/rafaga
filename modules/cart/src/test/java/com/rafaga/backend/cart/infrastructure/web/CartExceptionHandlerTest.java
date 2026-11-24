package com.rafaga.backend.cart.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.ApiError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class CartExceptionHandlerTest {
    @AfterEach
    void clearMdc() {
        MDC.remove(ApiError.CORRELATION_MDC_KEY);
    }

    @Test
    void mapsConcurrentActiveCartConstraintToStandardizedConflict() {
        MDC.put(ApiError.CORRELATION_MDC_KEY, "trace-123");

        var response = new CartExceptionHandler()
                .dataConflict(new DataIntegrityViolationException("duplicate active cart"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Cart conflicts with existing data");
        assertThat(response.getBody().traceId()).isEqualTo("trace-123");
    }
}
