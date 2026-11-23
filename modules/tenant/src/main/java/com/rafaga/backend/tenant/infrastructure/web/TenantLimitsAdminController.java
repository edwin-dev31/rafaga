package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.service.TenantLimitsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SUPER_ADMIN endpoints for inspecting and configuring tenant plan limits. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/limits")
public class TenantLimitsAdminController {
    private final TenantLimitsService limitsService;

    public TenantLimitsAdminController(TenantLimitsService limitsService) {
        this.limitsService = limitsService;
    }

    @GetMapping
    public TenantLimitsResponse getLimits(@PathVariable("tenantId") String tenantId) {
        return TenantLimitsResponse.from(limitsService.getLimits(tenantId));
    }

    @PutMapping
    public TenantLimitsResponse updateLimits(
            @PathVariable("tenantId") String tenantId,
            @Valid @RequestBody UpdateTenantLimitsRequest request) {
        return TenantLimitsResponse.from(limitsService.updateLimits(
                tenantId,
                request.planId(),
                request.maxProducts(),
                request.maxAssets(),
                request.maxStorageBytes(),
                request.maxAdminUsers(),
                request.maxDomains()));
    }

    @PostMapping("/sync")
    public TenantLimitsResponse syncWithPlan(@PathVariable("tenantId") String tenantId) {
        return TenantLimitsResponse.from(limitsService.syncWithCurrentPlan(tenantId));
    }

    public record UpdateTenantLimitsRequest(
            String planId,
            @PositiveOrZero Integer maxProducts,
            @PositiveOrZero Long maxAssets,
            @PositiveOrZero Long maxStorageBytes,
            @PositiveOrZero Integer maxAdminUsers,
            @PositiveOrZero Integer maxDomains) {
    }

    public record TenantLimitsResponse(
            String tenantId,
            String planId,
            int maxProducts,
            long maxAssets,
            long maxStorageBytes,
            int maxAdminUsers,
            int maxDomains,
            Instant updatedAt) {
        static TenantLimitsResponse from(TenantLimits limits) {
            return new TenantLimitsResponse(
                    limits.tenantId(),
                    limits.planId(),
                    limits.maxProducts(),
                    limits.maxAssets(),
                    limits.maxStorageBytes(),
                    limits.maxAdminUsers(),
                    limits.maxDomains(),
                    limits.updatedAt());
        }
    }
}
