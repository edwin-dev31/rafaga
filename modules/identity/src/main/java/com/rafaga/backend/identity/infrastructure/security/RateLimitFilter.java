package com.rafaga.backend.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies configured abuse limits after Spring Security has populated the principal. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitProperties properties, RateLimitService rateLimitService) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var rule = RateLimitRule.forRequest(request);
        if (rule.isEmpty() || !properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        RateLimitRule resolvedRule = rule.get();
        RateLimitService.RateLimitDecision decision = rateLimitService.check(
                resolvedRule,
                request.getServerName().toLowerCase(Locale.ROOT),
                resolvedRule.clientKey(request));
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
    }
}
