package com.rafaga.backend.infrastructure.web;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.media.application.MediaOperations;
import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import com.rafaga.backend.tenant.application.TenantOperationsQuery;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the current tenant's plan capabilities, feature flags, configured limits,
 * and effective resource usage for STORE_MANAGER and SUPER_ADMIN dashboards.
 *
 * <p>Downgrade policy: when a tenant moves to a lower plan whose limits are below
 * current usage, existing resources are preserved and remain readable/editable while
 * new creations are blocked with 409 PLAN_LIMIT_EXCEEDED until usage is within limit.
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantCapabilitiesController {
    public static final String DOWNGRADE_POLICY = "EXISTING_RESOURCES_PRESERVED_NEW_CREATIONS_BLOCKED";

    private final TenantRegistry tenantRegistry;
    private final TenantLimitsQuery limitsQuery;
    private final ProductService productService;
    private final UserService userService;
    private final ObjectProvider<MediaOperations> mediaOperationsProvider;
    private final ObjectProvider<TenantOperationsQuery> tenantOperationsProvider;

    public TenantCapabilitiesController(
            TenantRegistry tenantRegistry,
            TenantLimitsQuery limitsQuery,
            ProductService productService,
            UserService userService,
            ObjectProvider<MediaOperations> mediaOperationsProvider,
            ObjectProvider<TenantOperationsQuery> tenantOperationsProvider) {
        this.tenantRegistry = tenantRegistry;
        this.limitsQuery = limitsQuery;
        this.productService = productService;
        this.userService = userService;
        this.mediaOperationsProvider = mediaOperationsProvider;
        this.tenantOperationsProvider = tenantOperationsProvider;
    }

    @GetMapping("/capabilities")
    public TenantCapabilitiesResponse capabilities() {
        String tenantId = TenantContext.getCurrentTenantId();
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantLimits limits = limitsQuery.getLimits(tenantId);

        long usedProducts = productService.countProducts();
        long usedAdminUsers = userService.countUsers();

        long usedAssets = 0L;
        long usedStorageBytes = 0L;
        MediaOperations mediaOperations = mediaOperationsProvider.getIfAvailable();
        if (mediaOperations != null) {
            MediaOperations.TenantQuota quota = mediaOperations.quotaFor(tenantId);
            usedAssets = quota.usedAssets() + quota.reservedAssets();
            usedStorageBytes = quota.usedStorageBytes() + quota.reservedBytes();
        }

        long usedDomains = tenant.domains().size();
        TenantOperationsQuery operationsQuery = tenantOperationsProvider.getIfAvailable();
        if (operationsQuery != null) {
            int controlledDomains = operationsQuery.tenantSummary(tenantId).domains().size();
            if (controlledDomains > 0) {
                usedDomains = controlledDomains;
            }
        }

        return new TenantCapabilitiesResponse(
                tenant.tenantId(),
                tenant.name(),
                limits.planId(),
                tenant.status().name(),
                tenant.featureFlags(),
                new ResourceLimitsResponse(
                        UsageQuota.of(usedProducts, limits.maxProducts()),
                        UsageQuota.of(usedAssets, limits.maxAssets()),
                        UsageQuota.of(usedStorageBytes, limits.maxStorageBytes()),
                        UsageQuota.of(usedAdminUsers, limits.maxAdminUsers()),
                        UsageQuota.of(usedDomains, limits.maxDomains())),
                DOWNGRADE_POLICY,
                limits.updatedAt());
    }

    public record TenantCapabilitiesResponse(
            String tenantId,
            String name,
            String planId,
            String status,
            Map<String, Boolean> featureFlags,
            ResourceLimitsResponse limits,
            String downgradePolicy,
            Instant updatedAt) {
    }

    public record ResourceLimitsResponse(
            UsageQuota products,
            UsageQuota assets,
            UsageQuota storageBytes,
            UsageQuota adminUsers,
            UsageQuota domains) {
    }

    public record UsageQuota(long used, long limit, long remaining, boolean exceeded) {
        static UsageQuota of(long used, long limit) {
            return new UsageQuota(used, limit, Math.max(0L, limit - used), used > limit);
        }
    }
}
