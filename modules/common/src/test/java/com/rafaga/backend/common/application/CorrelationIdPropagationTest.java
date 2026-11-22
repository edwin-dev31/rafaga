package com.rafaga.backend.common.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdPropagationTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void attachesValidCorrelationIdFromMdcToOutboundRequests() {
        String validUuid = "af1c2d33-8500-4cd1-9012-1eb31a7baf32";
        MDC.put(CorrelationIdPropagation.MDC_KEY, validUuid);

        Map<String, String> headers = new LinkedHashMap<>();
        CorrelationIdPropagation.applyIfPresent(headers::putIfAbsent);

        assertThat(headers.get(CorrelationIdPropagation.HEADER)).isEqualTo(validUuid);
    }

    @Test
    void omitsUnsafeOrMissingCorrelationIdsFromOutboundRequests() {
        MDC.put(CorrelationIdPropagation.MDC_KEY, "forged\nvalue");

        Map<String, String> headers = new LinkedHashMap<>();
        CorrelationIdPropagation.applyIfPresent(headers::putIfAbsent);

        assertThat(headers).doesNotContainKey(CorrelationIdPropagation.HEADER);
    }
}
