package com.rafaga.backend.identity.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityResponseHeadersFilterTest {
    @Test
    void addsBrowserProtectionHeadersAndHstsForSecureRequests() throws Exception {
        SecurityResponseHeadersFilter filter = new SecurityResponseHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/storefront/products");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(response.getHeader("Strict-Transport-Security")).contains("max-age=31536000");
    }
}
