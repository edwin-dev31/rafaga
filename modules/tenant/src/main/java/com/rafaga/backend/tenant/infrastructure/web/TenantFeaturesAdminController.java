package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.service.FeatureFlagService;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SUPER_ADMIN endpoints for inspecting, overriding, and plan-syncing tenant feature flags. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/features")
public class TenantFeaturesAdminController {
    private final FeatureFlagService featureFlagService;

    public TenantFeaturesAdminController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    public TenantFeaturesResponse getFeatures(@PathVariable("tenantId") String tenantId) {
        return TenantFeaturesResponse.from(featureFlagService.getTenantFeatures(tenantId));
    }

    @PutMapping("/{featureName}")
    public TenantFeaturesResponse updateFeature(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("featureName") String featureName,
            @Valid @RequestBody UpdateFeatureFlagRequest request) {
        return TenantFeaturesResponse.from(
                featureFlagService.updateFeatureFlag(tenantId, featureName, request.enabled()));
    }

    @PostMapping("/sync")
    public TenantFeaturesResponse syncWithPlan(@PathVariable("tenantId") String tenantId) {
        return TenantFeaturesResponse.from(featureFlagService.syncWithCurrentPlan(tenantId));
    }

    public record UpdateFeatureFlagRequest(@NotNull Boolean enabled) {
    }

    public record TenantFeaturesResponse(
            String tenantId,
            String planId,
            Map<String, Boolean> featureFlags) {
        static TenantFeaturesResponse from(TenantConfig config) {
            return new TenantFeaturesResponse(config.tenantId(), config.planId(), config.featureFlags());
        }
    }
}
