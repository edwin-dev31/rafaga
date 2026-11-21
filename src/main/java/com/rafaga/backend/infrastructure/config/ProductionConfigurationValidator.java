package com.rafaga.backend.infrastructure.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Rejects present-but-unsafe production settings before the application serves traffic. */
@Component
@Profile("production")
public class ProductionConfigurationValidator implements SmartInitializingSingleton {
    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> issues = validate();
        if (!issues.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", issues));
        }
    }

    List<String> validate() {
        List<String> issues = new ArrayList<>();
        require(issues, "rafaga.identity.registration-token", 32);
        require(issues, "rafaga.tenant-directory.internal-token", 32);
        require(issues, "rafaga.identity.keycloak.client-secret", 16);
        require(issues, "rafaga.identity.keycloak.admin-client-secret", 16);
        require(issues, "media.r2.account-id", 1);
        require(issues, "media.r2.access-key-id", 1);
        require(issues, "media.r2.secret-access-key", 16);
        requireHttps(issues, "rafaga.identity.keycloak.issuer-uri");
        requireHttps(issues, "spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        requireHttps(issues, "media.r2.public-base-url");
        requireRedisEndpoint(issues);
        validateCors(issues);
        validateTrustedProxies(issues);
        return List.copyOf(issues);
    }

    private void require(List<String> issues, String property, int minimumLength) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank() || value.length() < minimumLength) {
            issues.add(property + " is missing or too short");
        }
    }

    private void requireHttps(List<String> issues, String property) {
        String value = environment.getProperty(property);
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                issues.add(property + " must be an absolute HTTPS URL");
            }
        } catch (Exception exception) {
            issues.add(property + " must be an absolute HTTPS URL");
        }
    }

    private void validateCors(List<String> issues) {
        String origins = environment.getProperty("rafaga.web.allowed-origins", "");
        if (origins.isBlank()) {
            issues.add("rafaga.web.allowed-origins is required");
            return;
        }
        for (String origin : origins.split(",")) {
            if (origin.contains("*") || !isHttpsOrigin(origin.trim())) {
                issues.add("rafaga.web.allowed-origins must contain explicit HTTPS origins only");
                return;
            }
        }
    }

    private void requireRedisEndpoint(List<String> issues) {
        String host = environment.getProperty("spring.data.redis.host", "");
        String clusterNodes = environment.getProperty("spring.data.redis.cluster.nodes", "");
        if (host.isBlank() && clusterNodes.isBlank()) {
            issues.add("a Redis host or cluster node list is required");
        }
    }

    private void validateTrustedProxies(List<String> issues) {
        String cidrs = environment.getProperty("rafaga.web.trusted-proxy.cidrs", "");
        if (cidrs.isBlank()) {
            issues.add("rafaga.web.trusted-proxy.cidrs is required");
        } else if (cidrs.contains("0.0.0.0/0") || cidrs.contains("::/0")) {
            issues.add("rafaga.web.trusted-proxy.cidrs must not trust all addresses");
        }
    }

    private boolean isHttpsOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getPath().isEmpty() && uri.getQuery() == null && uri.getFragment() == null;
        } catch (Exception exception) {
            return false;
        }
    }
}
