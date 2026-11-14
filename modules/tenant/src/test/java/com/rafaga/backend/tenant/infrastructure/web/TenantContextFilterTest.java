package com.rafaga.backend.tenant.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.config.TenantHeaderProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesAndClearsTenantContextForRegisteredActiveTenant() throws Exception {
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantConfig tenant = new TenantConfig("tenant-a", "Tenant A", List.of("a.example.test"),
                DeploymentMode.SHARED_DB, null, "test", TenantStatus.ACTIVE, Map.of());
        when(registry.findByDomain("a.example.test")).thenReturn(Optional.of(tenant));
        when(registry.findById("tenant-a")).thenReturn(Optional.of(tenant));
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        CompositeTenantResolver resolver = new CompositeTenantResolver(
                new DomainTenantResolver(registryProvider, ""), new JwtClaimTenantResolver(),
                new InternalHeaderTenantResolver(new TenantHeaderProperties()));
        TenantContextFilter filter = new TenantContextFilter(resolver, registryProvider);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setServerName("a.example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void resolvesTenantContextForPublicAuthenticationEndpoints() {
        TenantContextFilter filter = new TenantContextFilter(mock(CompositeTenantResolver.class), mock(ObjectProvider.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/auth/login");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void skipsDocumentationAndErrorEndpoints() {
        TenantContextFilter filter = new TenantContextFilter(mock(CompositeTenantResolver.class), mock(ObjectProvider.class));

        for (String path : List.of("/scalar", "/scalar/scalar.js", "/v3/api-docs",
                "/v3/api-docs/swagger-config", "/error", "/favicon.svg")) {
            assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", path)))
                    .as(path)
                    .isTrue();
        }
    }

    @Test
    void rejectsAuthenticatedRequestForAnotherTenant() throws Exception {
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantConfig tenant = new TenantConfig("tenant-a", "Tenant A", List.of("a.example.test"),
                DeploymentMode.SHARED_DB, null, "test", TenantStatus.ACTIVE, Map.of());
        when(registry.findByDomain("a.example.test")).thenReturn(Optional.of(tenant));
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        CompositeTenantResolver resolver = new CompositeTenantResolver(
                new DomainTenantResolver(registryProvider, ""), new JwtClaimTenantResolver(),
                new InternalHeaderTenantResolver(new TenantHeaderProperties()));
        TenantContextFilter filter = new TenantContextFilter(resolver, registryProvider);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user")
                .claim("tenant_id", "tenant-b").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setServerName("a.example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        assertThat(resolver.resolve(request)).contains("tenant-a");
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isInstanceOf(Jwt.class);

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertThat(response.getStatus()).as(response.getErrorMessage()).isEqualTo(403);
        assertThat(invoked).isFalse();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void logsTenantResolutionFailuresWithoutRequestData() throws Exception {
        CompositeTenantResolver resolver = mock(CompositeTenantResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        TenantContextFilter filter = new TenantContextFilter(resolver, mock(ObjectProvider.class));
        Logger logger = (Logger) LoggerFactory.getLogger(TenantContextFilter.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);

        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), new MockHttpServletResponse(),
                    (request, response) -> { });
            assertThat(events.list).anySatisfy(event ->
                    assertThat(event.getFormattedMessage()).contains("Tenant resolution failed"));
            assertThat(events.list).anySatisfy(event ->
                    assertThat(event.getMDCPropertyMap())
                            .containsEntry(TenantLoggingInterceptor.TENANT_ID, TenantLoggingInterceptor.SYSTEM_TENANT));
        } finally {
            logger.detachAppender(events);
        }
    }
}
