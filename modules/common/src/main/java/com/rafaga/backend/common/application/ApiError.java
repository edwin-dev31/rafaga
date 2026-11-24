package com.rafaga.backend.common.application;

import java.util.Map;
import org.slf4j.MDC;

/**
 * Standardized API error response contract: {@code { code, message, fields?, traceId }}.
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> fields,
        String traceId) {

    public static final String CORRELATION_MDC_KEY = "correlationId";

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, currentTraceId());
    }

    public static ApiError validation(String message, Map<String, String> fields) {
        Map<String, String> normalized = (fields == null || fields.isEmpty()) ? null : Map.copyOf(fields);
        return new ApiError("VALIDATION_ERROR", message, normalized, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get(CORRELATION_MDC_KEY);
    }
}
