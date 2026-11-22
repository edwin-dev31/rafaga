package com.rafaga.backend.common.application;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.MDC;

/**
 * Propagates the current request's validated correlation ID to outbound
 * dependency requests without introducing a distributed tracing collector.
 */
public final class CorrelationIdPropagation {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdPropagation() {
    }

    public static void applyIfPresent(BiConsumer<String, String> headerSetter) {
        String correlationId = currentCorrelationId();
        if (correlationId != null) {
            headerSetter.accept(HEADER, correlationId);
        }
    }

    public static String currentCorrelationId() {
        String value = MDC.get(MDC_KEY);
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
