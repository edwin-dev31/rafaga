package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.application.resolution.TenantResolver;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DomainTenantResolver implements TenantResolver {
    private final ObjectProvider<TenantRegistry> registryProvider;
    private final String devDefaultTenant;

    public DomainTenantResolver(
            ObjectProvider<TenantRegistry> registryProvider,
            @Value("${rafaga.tenant.dev-default-tenant:}") String devDefaultTenant) {
        this.registryProvider = registryProvider;
        this.devDefaultTenant = devDefaultTenant;
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        TenantRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return Optional.empty();
        }
        String serverName = request.getServerName();
        Optional<String> resolved = registry.findByDomain(serverName)
                .map(TenantConfig::tenantId);
        if (resolved.isPresent()) {
            return resolved;
        }
        if (!devDefaultTenant.isBlank() && isLoopback(serverName)) {
            return Optional.of(devDefaultTenant);
        }
        return Optional.empty();
    }

    private boolean isLoopback(String serverName) {
        return "localhost".equalsIgnoreCase(serverName)
                || "127.0.0.1".equals(serverName)
                || "::1".equals(serverName)
                || "[::1]".equals(serverName);
    }
}
