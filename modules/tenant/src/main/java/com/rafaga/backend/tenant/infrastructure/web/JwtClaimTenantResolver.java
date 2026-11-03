package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtClaimTenantResolver implements TenantResolver {
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt
                ? Optional.ofNullable(jwt.getClaimAsString("tenant_id")) : Optional.empty();
    }
}
