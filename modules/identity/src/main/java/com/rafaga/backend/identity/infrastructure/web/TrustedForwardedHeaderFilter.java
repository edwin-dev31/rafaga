package com.rafaga.backend.identity.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts X-Forwarded headers only from explicitly configured proxy networks.
 * Direct callers cannot select a tenant host or client IP by supplying headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TrustedForwardedHeaderFilter extends OncePerRequestFilter {
    private static final Set<String> FORWARDED_HEADERS = Set.of("forwarded");

    private final TrustedProxyProperties properties;
    private final ForwardedHeaderFilter forwardedHeaderFilter = new ForwardedHeaderFilter(false);

    public TrustedForwardedHeaderFilter(TrustedProxyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isTrustedProxy(request.getRemoteAddr())) {
            filterChain.doFilter(new ForwardedHeaderStrippingRequest(request, true), response);
            return;
        }
        // The gateway uses X-Forwarded-*; never let an alternate Forwarded
        // header influence downstream code, even from a trusted proxy.
        forwardedHeaderFilter.doFilter(new ForwardedHeaderStrippingRequest(request, false), response, filterChain);
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return properties.getCidrs().stream()
                .filter(cidr -> cidr != null && !cidr.isBlank())
                .map(CidrRange::parse)
                .anyMatch(range -> range.contains(remoteAddress));
    }

    private record CidrRange(InetAddress network, int prefixLength) {
        static CidrRange parse(String value) {
            String[] parts = value.trim().split("/", -1);
            if (parts.length != 2) {
                throw new IllegalStateException("Trusted proxy CIDR must use address/prefix notation");
            }
            try {
                InetAddress network = InetAddress.getByName(parts[0]);
                int prefixLength = Integer.parseInt(parts[1]);
                int addressBits = network.getAddress().length * Byte.SIZE;
                if (prefixLength < 0 || prefixLength > addressBits) {
                    throw new IllegalStateException("Trusted proxy CIDR has an invalid prefix length");
                }
                return new CidrRange(network, prefixLength);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalStateException("Trusted proxy CIDR is invalid", exception);
            }
        }

        boolean contains(String candidate) {
            try {
                byte[] expected = network.getAddress();
                byte[] actual = InetAddress.getByName(candidate).getAddress();
                if (expected.length != actual.length) {
                    return false;
                }
                int completeBytes = prefixLength / Byte.SIZE;
                int remainingBits = prefixLength % Byte.SIZE;
                for (int index = 0; index < completeBytes; index++) {
                    if (expected[index] != actual[index]) {
                        return false;
                    }
                }
                if (remainingBits == 0) {
                    return true;
                }
                int mask = 0xFF << (Byte.SIZE - remainingBits);
                return (expected[completeBytes] & mask) == (actual[completeBytes] & mask);
            } catch (UnknownHostException exception) {
                return false;
            }
        }
    }

    private static final class ForwardedHeaderStrippingRequest extends HttpServletRequestWrapper {
        private final boolean stripAllForwardedHeaders;

        ForwardedHeaderStrippingRequest(HttpServletRequest request, boolean stripAllForwardedHeaders) {
            super(request);
            this.stripAllForwardedHeaders = stripAllForwardedHeaders;
        }

        @Override
        public String getHeader(String name) {
            return isStripped(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isStripped(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> visibleNames = new ArrayList<>();
            Enumeration<String> names = super.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!isStripped(name)) {
                    visibleNames.add(name);
                }
            }
            return Collections.enumeration(visibleNames);
        }

        private boolean isStripped(String name) {
            if (name == null) {
                return false;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            return FORWARDED_HEADERS.contains(normalized)
                    || (stripAllForwardedHeaders && normalized.startsWith("x-forwarded-"));
        }
    }
}
