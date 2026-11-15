package com.rafaga.backend.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {
    @Test
    void propagatesAValidClientCorrelationIdAndClearsItAfterTheRequest() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "af1c2d33-8500-4cd1-9012-1eb31a7baf32");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER))
                .isEqualTo("af1c2d33-8500-4cd1-9012-1eb31a7baf32");
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeClientCorrelationIds() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "forged\nlog-entry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).doesNotContain("forged");
        assertThat(UUID.fromString(response.getHeader(RequestCorrelationFilter.HEADER))).isNotNull();
    }
}
