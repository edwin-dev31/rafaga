package com.rafaga.backend.tenant.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TenantConfig(String tenantId, String name, List<String> domains,
                           DeploymentMode deploymentMode, DatabaseConfig databaseConfig,
                           String planId, TenantStatus status, Map<String, Boolean> featureFlags) {
    public TenantConfig {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        domains = List.copyOf(domains);
        Objects.requireNonNull(deploymentMode, "deploymentMode must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        featureFlags = Map.copyOf(featureFlags);
    }
}
