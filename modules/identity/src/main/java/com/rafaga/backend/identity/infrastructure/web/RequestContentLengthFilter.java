package com.rafaga.backend.identity.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects API bodies whose declared size exceeds the application contract. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestContentLengthFilter extends OncePerRequestFilter {
    private final HttpRequestLimitsProperties properties;

    public RequestContentLengthFilter(HttpRequestLimitsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > properties.getMaxContentLength().toBytes()) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"Request body is too large\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
