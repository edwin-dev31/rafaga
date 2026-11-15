package com.rafaga.backend.infrastructure.web.admin;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final TenantRegistry tenantRegistry;
    private final TenantAwareRoutingDataSource dataSource;

    public SystemAdministrationController(Map<String, HealthIndicator> healthIndicators,
            CacheManager cacheManager, TenantRegistry tenantRegistry,
            TenantAwareRoutingDataSource dataSource) {
        this.healthIndicators = Map.copyOf(healthIndicators);
        this.cacheManager = cacheManager;
        this.tenantRegistry = tenantRegistry;
        this.dataSource = dataSource;
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

    @GetMapping("/operations/overview")
    public PlatformOverviewResponse overview() {
        List<com.rafaga.backend.tenant.domain.model.TenantConfig> tenants = tenantRegistry.findAll();
        HikariDataSource sharedPool = dataSource.sharedDataSource();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Instant startedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        return new PlatformOverviewResponse(
                startedAt,
                Duration.between(startedAt, Instant.now()).toSeconds(),
                new JvmMemoryResponse(heap.getUsed(), heap.getCommitted(), heap.getMax()),
                PoolResponse.from(sharedPool),
                health(),
                new CacheResponse(cacheManager.getCacheNames().stream().sorted().toList()),
                new TenantCountResponse(
                        tenants.size(),
                        tenants.stream().filter(tenant -> tenant.status() == TenantStatus.ACTIVE).count(),
                        tenants.stream().filter(tenant -> tenant.deploymentMode() == DeploymentMode.DEDICATED_DB).count(),
                        tenants.stream().mapToLong(tenant -> tenant.domains().size()).sum()));
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

    public record PlatformOverviewResponse(
            Instant startedAt,
            long uptimeSeconds,
            JvmMemoryResponse jvmMemory,
            PoolResponse sharedDatabasePool,
            HealthResponse dependencies,
            CacheResponse cache,
            TenantCountResponse tenants) { }

    public record JvmMemoryResponse(long usedBytes, long committedBytes, long maxBytes) { }

    public record PoolResponse(int active, int idle, int total, int maximum, int pending) {
        static PoolResponse from(HikariDataSource pool) {
            var metrics = pool.getHikariPoolMXBean();
            if (metrics == null) {
                return new PoolResponse(0, 0, 0, pool.getMaximumPoolSize(), 0);
            }
            return new PoolResponse(metrics.getActiveConnections(), metrics.getIdleConnections(),
                    metrics.getTotalConnections(), pool.getMaximumPoolSize(), metrics.getThreadsAwaitingConnection());
        }
    }

    public record CacheResponse(List<String> configuredRegions) { }

    public record TenantCountResponse(long total, long active, long dedicatedDatabases, long domains) { }
}
