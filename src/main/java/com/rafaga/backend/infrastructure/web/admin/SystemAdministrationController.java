package com.rafaga.backend.infrastructure.web.admin;

import com.rafaga.backend.common.application.JobOutcomeRegistry;
import com.rafaga.backend.media.application.MediaOperations;
import com.rafaga.backend.tenant.application.PlatformDataSourceMetrics;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final PlatformDataSourceMetrics dataSourceMetrics;
    private final JobOutcomeRegistry jobOutcomes;
    private final MediaOperations mediaOperations;

    public SystemAdministrationController(Map<String, HealthIndicator> healthIndicators,
            CacheManager cacheManager, TenantRegistry tenantRegistry,
            PlatformDataSourceMetrics dataSourceMetrics, JobOutcomeRegistry jobOutcomes,
            MediaOperations mediaOperations) {
        this.healthIndicators = Map.copyOf(healthIndicators);
        this.cacheManager = cacheManager;
        this.tenantRegistry = tenantRegistry;
        this.dataSourceMetrics = dataSourceMetrics;
        this.jobOutcomes = jobOutcomes;
        this.mediaOperations = mediaOperations;
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
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Instant startedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        return new PlatformOverviewResponse(
                startedAt,
                Duration.between(startedAt, Instant.now()).toSeconds(),
                new JvmMemoryResponse(heap.getUsed(), heap.getCommitted(), heap.getMax()),
                PoolResponse.from(dataSourceMetrics.sharedPoolMetrics()),
                health(),
                new CacheResponse(cacheManager.getCacheNames().stream().sorted().toList()),
                jobOutcomes.snapshot(),
                new TenantCountResponse(
                        tenants.size(),
                        tenants.stream().filter(tenant -> tenant.status() == TenantStatus.ACTIVE).count(),
                        tenants.stream().filter(tenant -> tenant.deploymentMode() == DeploymentMode.DEDICATED_DB).count(),
                        tenants.stream().mapToLong(tenant -> tenant.domains().size()).sum()));
    }

    @GetMapping("/tenants/{tenantId}/operations")
    public TenantOperationsResponse tenantOperations(@PathVariable("tenantId") String tenantId) {
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        MediaOperations.TenantQuota quota = mediaOperations.quotaFor(tenantId);
        return new TenantOperationsResponse(tenant.tenantId(), tenant.status().name(),
                tenant.deploymentMode().name(), tenant.domains().size(),
                tenant.databaseConfig() != null, QuotaResponse.from(quota));
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
            List<JobOutcomeRegistry.Outcome> recentJobs,
            TenantCountResponse tenants) { }

    public record JvmMemoryResponse(long usedBytes, long committedBytes, long maxBytes) { }

    public record PoolResponse(int active, int idle, int total, int maximum, int pending) {
        static PoolResponse from(PlatformDataSourceMetrics.PoolMetrics metrics) {
            return new PoolResponse(metrics.active(), metrics.idle(), metrics.total(),
                    metrics.maximum(), metrics.pending());
        }
    }

    public record CacheResponse(List<String> configuredRegions) { }

    public record TenantCountResponse(long total, long active, long dedicatedDatabases, long domains) { }

    public record TenantOperationsResponse(String tenantId, String status, String deploymentMode,
            int domainCount, boolean dedicatedDatabaseConfigured, QuotaResponse mediaQuota) { }

    public record QuotaResponse(long maxAssets, long maxStorageBytes, long usedAssets,
            long usedStorageBytes, long reservedAssets, long reservedBytes) {
        static QuotaResponse from(MediaOperations.TenantQuota quota) {
            return new QuotaResponse(quota.maxAssets(), quota.maxStorageBytes(), quota.usedAssets(),
                    quota.usedStorageBytes(), quota.reservedAssets(), quota.reservedBytes());
        }
    }
}
