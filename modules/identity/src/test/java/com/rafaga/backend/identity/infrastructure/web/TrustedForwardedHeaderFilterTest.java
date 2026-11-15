package com.rafaga.backend.identity.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedForwardedHeaderFilterTest {
    @Test
    void stripsForwardedHeadersFromAnUntrustedCaller() throws Exception {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setCidrs(List.of("10.0.0.0/8"));
        TrustedForwardedHeaderFilter filter = new TrustedForwardedHeaderFilter(properties);
        MockHttpServletRequest request = request("198.51.100.9");
        request.addHeader("Forwarded", "host=other.example.test;proto=https");
        request.addHeader("X-Forwarded-For", "203.0.113.20");
        request.addHeader("X-Forwarded-Host", "other.example.test");

        HttpServletRequest passed = filter(filter, request);

        assertThat(passed.getServerName()).isEqualTo("backend.internal.test");
        assertThat(passed.getRemoteAddr()).isEqualTo("198.51.100.9");
        assertThat(passed.getHeader("Forwarded")).isNull();
        assertThat(passed.getHeader("X-Forwarded-For")).isNull();
        assertThat(passed.getHeader("X-Forwarded-Host")).isNull();
    }

    @Test
    void acceptsXForwardedContextOnlyFromATrustedGatewayRange() throws Exception {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setCidrs(List.of("10.0.0.0/8"));
        TrustedForwardedHeaderFilter filter = new TrustedForwardedHeaderFilter(properties);
        MockHttpServletRequest request = request("10.12.0.4");
        request.addHeader("Forwarded", "host=malicious.example.test;proto=http");
        request.addHeader("X-Forwarded-For", "203.0.113.20");
        request.addHeader("X-Forwarded-Host", "shop.example.test");
        request.addHeader("X-Forwarded-Proto", "https");

        HttpServletRequest passed = filter(filter, request);

        assertThat(passed.getServerName()).isEqualTo("shop.example.test");
        assertThat(passed.getRemoteAddr()).isEqualTo("203.0.113.20");
        assertThat(passed.getScheme()).isEqualTo("https");
        assertThat(passed.getHeader("Forwarded")).isNull();
        assertThat(passed.getHeader("X-Forwarded-For")).isNull();
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/storefront/products");
        request.setServerName("backend.internal.test");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private HttpServletRequest filter(TrustedForwardedHeaderFilter filter, MockHttpServletRequest request)
            throws Exception {
        AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (passedRequest, ignoredResponse) ->
                captured.set((HttpServletRequest) passedRequest));
        return captured.get();
    }
}
