package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.application.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class DomainTenantResolver implements TenantResolver {
    private final ObjectProvider<TenantRegistry> registryProvider;
    public DomainTenantResolver(ObjectProvider<TenantRegistry> registryProvider) { this.registryProvider = registryProvider; }
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        TenantRegistry registry = registryProvider.getIfAvailable();
        return registry == null ? Optional.empty() : registry.findByDomain(request.getServerName()).map(config -> config.tenantId());
    }
}
