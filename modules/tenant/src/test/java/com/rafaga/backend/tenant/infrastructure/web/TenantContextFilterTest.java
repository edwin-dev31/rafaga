package com.rafaga.backend.tenant.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.config.TenantHeaderProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
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
                new DomainTenantResolver(registryProvider), new JwtClaimTenantResolver(),
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
}
