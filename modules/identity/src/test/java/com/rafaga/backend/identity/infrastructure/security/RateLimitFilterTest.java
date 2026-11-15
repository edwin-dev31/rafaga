package com.rafaga.backend.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {
    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        properties = new RateLimitProperties();
        properties.setWindow(java.time.Duration.ofMinutes(1));
        properties.setLoginPerWindow(4);
        properties.setRegistrationPerWindow(4);
        properties.setMediaUploadPerWindow(4);
        properties.setMediaConfirmationPerWindow(4);
        properties.setGuestCartMutationPerWindow(4);
        properties.setCheckoutPerWindow(4);
        properties.setOrderCreationPerWindow(4);
        RateLimitService service = new RateLimitService(properties, redisTemplateProvider,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        filter = new RateLimitFilter(properties, service);
    }

    @Test
    void rejectsLoginAfterTheStricterRedisFallbackLimit() throws Exception {
        assertThat(send("POST", "/api/public/auth/login", "198.51.100.10").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/public/auth/login", "198.51.100.10").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = send("POST", "/api/public/auth/login", "198.51.100.10");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void keepsRateLimitsSeparateForDifferentClientAddresses() throws Exception {
        assertThat(send("POST", "/api/public/auth/register", "198.51.100.11").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/public/auth/register", "198.51.100.12").getStatus()).isEqualTo(200);
    }

    @Test
    void limitsEachExpensiveRouteType() throws Exception {
        assertThat(send("POST", "/api/media/upload/batch", "198.51.100.13").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/media/upload/batch/confirm", "198.51.100.13").getStatus()).isEqualTo(200);
        assertThat(send("PUT", "/api/storefront/carts/current/items", "198.51.100.13").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/storefront/carts/current/checkout", "198.51.100.13").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/orders", "198.51.100.13").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitUnmatchedReadRoutes() throws Exception {
        for (int request = 0; request < 10; request++) {
            assertThat(send("GET", "/api/storefront/products", "198.51.100.14").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(String method, String path, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServerName("shop.example.test");
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
