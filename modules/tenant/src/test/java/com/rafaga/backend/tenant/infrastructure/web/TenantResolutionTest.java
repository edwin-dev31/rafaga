package com.rafaga.backend.tenant.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.config.TenantHeaderProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class TenantResolutionTest {

    @Test
    void acceptsInternalHeaderOnlyFromAnAllowlistedAddress() {
        TenantHeaderProperties properties = new TenantHeaderProperties();
        properties.setEnabled(true);
        properties.setTrustedCidrs(List.of("127.0.0.1/32"));
        InternalHeaderTenantResolver resolver = new InternalHeaderTenantResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Tenant-ID", "tenant-internal");

        assertThat(resolver.resolve(request)).contains("tenant-internal");

        request.setRemoteAddr("203.0.113.10");
        assertThat(resolver.resolve(request)).isEmpty();
    }

    @Test
    void domainResolutionTakesPrecedenceOverTheInternalHeader() {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findByDomain("shop.example.test")).thenReturn(Optional.of(activeTenant("tenant-domain")));
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(registry);

        TenantHeaderProperties properties = new TenantHeaderProperties();
        properties.setEnabled(true);
        properties.setTrustedCidrs(List.of("127.0.0.1/32"));
        CompositeTenantResolver resolver = new CompositeTenantResolver(
                new DomainTenantResolver(registryProvider),
                new JwtClaimTenantResolver(),
                new InternalHeaderTenantResolver(properties));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("shop.example.test");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Tenant-ID", "tenant-header");

        assertThat(resolver.resolve(request)).contains("tenant-domain");
    }

    private TenantConfig activeTenant(String id) {
        return new TenantConfig(id, "Test tenant", List.of("shop.example.test"), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of());
    }
}
