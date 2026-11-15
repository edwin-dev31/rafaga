package com.rafaga.backend.identity.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

class RequestContentLengthFilterTest {
    @Test
    void rejectsBodiesLargerThanTheConfiguredLimit() throws Exception {
        HttpRequestLimitsProperties properties = new HttpRequestLimitsProperties();
        properties.setMaxContentLength(DataSize.ofBytes(10));
        RequestContentLengthFilter filter = new RequestContentLengthFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
    }

    @Test
    void allowsBodiesWithinTheConfiguredLimit() throws Exception {
        HttpRequestLimitsProperties properties = new HttpRequestLimitsProperties();
        properties.setMaxContentLength(DataSize.ofBytes(10));
        RequestContentLengthFilter filter = new RequestContentLengthFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(new byte[10]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
