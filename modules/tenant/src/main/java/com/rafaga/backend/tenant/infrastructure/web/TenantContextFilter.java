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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Runs after security so a validated JWT claim can be considered. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
    private final CompositeTenantResolver tenantResolver;
    private final ObjectProvider<TenantRegistry> registryProvider;

    public TenantContextFilter(
            CompositeTenantResolver tenantResolver,
            ObjectProvider<TenantRegistry> registryProvider) {
        this.tenantResolver = tenantResolver;
        this.registryProvider = registryProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/error")
                || path.equals("/scalar") || path.startsWith("/scalar/")
                || path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/")
                || path.equals("/favicon.ico") || path.equals("/favicon.svg")
                || path.startsWith("/actuator/health")
                || path.startsWith("/api/internal/tenant-directory")
                || (path.startsWith("/api/public/") && !path.startsWith("/api/public/auth/"))
                || path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {
        MDC.put(TenantLoggingInterceptor.TENANT_ID, TenantLoggingInterceptor.SYSTEM_TENANT);
        try {
            String tenantId = tenantResolver.resolve(request).orElse(null);
            if (tenantId == null) {
                if (isTenantAgnosticAuthPath(request.getRequestURI())) {
                    chain.doFilter(request, response);
                    return;
                }
                log.warn("Tenant resolution failed");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to determine tenant for request");
                return;
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Jwt jwt
                    && !isSuperAdmin(authentication)
                    && !tenantId.equals(jwt.getClaimAsString("tenant_id"))) {
                log.warn("Tenant resolution rejected because the JWT tenant does not match");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Authenticated tenant does not match request tenant");
                return;
            }
            TenantRegistry registry = registryProvider.getIfAvailable();
            if (registry == null) {
                log.warn("Tenant resolution failed because the tenant registry is unavailable");
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Tenant registry is unavailable");
                return;
            }
            TenantConfig tenant = registry.findById(tenantId).orElse(null);
            if (tenant == null) {
                log.warn("Tenant resolution failed because the resolved tenant is not registered");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Resolved tenant is not registered");
                return;
            }
            if (tenant.status() != TenantStatus.ACTIVE) {
                log.warn("Tenant resolution rejected because the tenant is inactive");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is inactive");
                return;
            }
            TenantContext.setCurrentTenantId(tenantId);
            MDC.put(TenantLoggingInterceptor.TENANT_ID, tenantId);
            log.info("Tenant resolved for request");
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TenantLoggingInterceptor.TENANT_ID);
        }
    }

    private boolean isTenantAgnosticAuthPath(String path) {
        return "/api/public/auth/login".equals(path)
                || "/api/public/auth/refresh".equals(path)
                || "/api/public/auth/logout".equals(path);
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
