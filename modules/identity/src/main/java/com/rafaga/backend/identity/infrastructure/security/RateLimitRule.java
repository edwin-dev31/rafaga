package com.rafaga.backend.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

enum RateLimitRule {
    LOGIN("login") {
        @Override
        boolean matches(String method, String path) {
            return "POST".equals(method) && "/api/public/auth/login".equals(path);
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getLoginPerWindow();
        }

        @Override
        String clientKey(HttpServletRequest request) {
            return remoteAddress(request);
        }
    },
    REGISTRATION("registration") {
        @Override
        boolean matches(String method, String path) {
            return "POST".equals(method) && "/api/public/auth/register".equals(path);
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getRegistrationPerWindow();
        }

        @Override
        String clientKey(HttpServletRequest request) {
            return remoteAddress(request);
        }
    },
    MEDIA_UPLOAD("media-upload") {
        @Override
        boolean matches(String method, String path) {
            return "POST".equals(method) && "/api/media/upload/batch".equals(path);
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getMediaUploadPerWindow();
        }
    },
    MEDIA_CONFIRMATION("media-confirmation") {
        @Override
        boolean matches(String method, String path) {
            return "POST".equals(method) && "/api/media/upload/batch/confirm".equals(path);
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getMediaConfirmationPerWindow();
        }
    },
    GUEST_CART_MUTATION("guest-cart-mutation") {
        @Override
        boolean matches(String method, String path) {
            return ("PUT".equals(method) && "/api/storefront/carts/current/items".equals(path))
                    || ("DELETE".equals(method) && path.startsWith("/api/storefront/carts/current/items/"));
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getGuestCartMutationPerWindow();
        }
    },
    CHECKOUT("checkout") {
        @Override
        boolean matches(String method, String path) {
            return "POST".equals(method)
                    && ("/api/storefront/carts/current/checkout".equals(path)
                    || "/api/carts/checkout".equals(path));
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getCheckoutPerWindow();
        }
    },
    ORDER_CREATION("order-creation") {
        @Override
        boolean matches(String method, String path) {
            return "POST".equals(method) && "/api/orders".equals(path);
        }

        @Override
        int limit(RateLimitProperties properties) {
            return properties.getOrderCreationPerWindow();
        }
    };

    private final String name;

    RateLimitRule(String name) {
        this.name = name;
    }

    static Optional<RateLimitRule> forRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (RateLimitRule rule : values()) {
            if (rule.matches(method, path)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    String nameValue() {
        return name;
    }

    abstract boolean matches(String method, String path);

    abstract int limit(RateLimitProperties properties);

    String clientKey(HttpServletRequest request) {
        String authenticatedSubject = RateLimitClientIdentity.authenticatedSubject();
        if (authenticatedSubject != null) {
            return "subject:" + authenticatedSubject;
        }
        String guestSession = request.getHeader("Cookie");
        if (guestSession != null) {
            String resolved = RateLimitClientIdentity.guestSession(request);
            if (resolved != null) {
                return "guest:" + resolved;
            }
        }
        return remoteAddress(request);
    }

    static String remoteAddress(HttpServletRequest request) {
        return "ip:" + request.getRemoteAddr();
    }
}
