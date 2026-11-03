package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Public resolution takes precedence; the internal header is a restricted fallback. */
@Component
public class CompositeTenantResolver implements TenantResolver {
    private final List<TenantResolver> resolvers;
    public CompositeTenantResolver(DomainTenantResolver domain, JwtClaimTenantResolver jwt, InternalHeaderTenantResolver header) {
        resolvers = List.of(domain, jwt, header);
    }
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        return resolvers.stream().map(resolver -> resolver.resolve(request)).flatMap(Optional::stream).findFirst();
    }
}
