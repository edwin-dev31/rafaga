package com.rafaga.backend.infrastructure.web.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin-only operational endpoints. Responses intentionally contain no
 * connection strings, credentials, tenant identifiers, or exception messages.
 */
@RestController
@RequestMapping("/api/admin")
public class SystemAdministrationController {
    private static final Map<String, String> CACHE_NAMES = Map.of(
            "tenants", "tenants",
            "feature-flags", "feature-flags",
            "products", "products",
            "categories", "categories");

    private final Map<String, HealthIndicator> healthIndicators;
    private final CacheManager cacheManager;

    public SystemAdministrationController(Map<String, HealthIndicator> healthIndicators,
            CacheManager cacheManager) {
        this.healthIndicators = Map.copyOf(healthIndicators);
        this.cacheManager = cacheManager;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("sharedDatabase", statusOf("sharedDatabaseHealthIndicator"));
        components.put("dedicatedDatabases", statusOf("dedicatedDatabaseHealthIndicator"));
        components.put("redis", statusOf("redisHealthIndicator"));
        components.put("keycloak", statusOf("keycloakHealthIndicator"));
        return new HealthResponse(overallStatus(components), Map.copyOf(components));
    }

    @PostMapping("/cache/invalidate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invalidateCache(@RequestParam(name = "cache") String cache) {
        String cacheName = CACHE_NAMES.get(cache);
        if (cacheName == null) {
            throw new IllegalArgumentException("Unsupported cache: " + cache);
        }
        Cache target = cacheManager.getCache(cacheName);
        if (target != null) {
            target.clear();
        }
    }

    private String statusOf(String indicatorName) {
        HealthIndicator indicator = healthIndicators.get(indicatorName);
        if (indicator == null) {
            return "UNKNOWN";
        }
        Health health = indicator.health();
        return health.getStatus().getCode();
    }

    private String overallStatus(Map<String, String> components) {
        if (components.containsValue("DOWN") || components.containsValue("OUT_OF_SERVICE")) {
            return "DOWN";
        }
        if (components.containsValue("DEGRADED")) {
            return "DEGRADED";
        }
        return "UP";
    }

    public record HealthResponse(String status, Map<String, String> components) { }
}
