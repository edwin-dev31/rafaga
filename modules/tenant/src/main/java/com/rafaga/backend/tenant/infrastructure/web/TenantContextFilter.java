package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after security so a validated JWT claim can be considered. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {
    private final CompositeTenantResolver tenantResolver;
    private final ObjectProvider<TenantRegistry> registryProvider;

    public TenantContextFilter(CompositeTenantResolver tenantResolver, ObjectProvider<TenantRegistry> registryProvider) {
        this.tenantResolver = tenantResolver;
        this.registryProvider = registryProvider;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.startsWith("/api/public/") || path.startsWith("/api/admin/");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantId = tenantResolver.resolve(request).orElse(null);
        if (tenantId == null) { response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to determine tenant for request"); return; }
        TenantRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) { response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Tenant registry is unavailable"); return; }
        TenantConfig tenant = registry.findById(tenantId).orElse(null);
        if (tenant == null) { response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Resolved tenant is not registered"); return; }
        if (tenant.status() != TenantStatus.ACTIVE) { response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is inactive"); return; }
        try { TenantContext.setCurrentTenantId(tenantId); chain.doFilter(request, response); }
        finally { TenantContext.clear(); }
    }
}
