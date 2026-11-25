package com.rafaga.backend.inventory.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.ApiError;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

class StockExceptionHandlerTest {
    private final StockExceptionHandler handler = new StockExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.remove(ApiError.CORRELATION_MDC_KEY);
    }

    @Test
    void emitsStandardApiErrorWithCodeAndTraceId() {
        MDC.put(ApiError.CORRELATION_MDC_KEY, "trace-stock-1");

        var notFound = handler.notFound(new StockNotFoundException("Product variant not found"));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(notFound.getBody().traceId()).isEqualTo("trace-stock-1");

        var insufficient = handler.insufficient(new InsufficientStockException("Not enough stock"));
        assertThat(insufficient.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(insufficient.getBody()).isNotNull();
        assertThat(insufficient.getBody().code()).isEqualTo("INSUFFICIENT_STOCK");
    }
}
