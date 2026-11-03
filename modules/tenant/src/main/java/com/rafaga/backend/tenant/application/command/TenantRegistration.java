package com.rafaga.backend.tenant.application.command;

import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import java.util.List;
import java.util.Objects;

public record TenantRegistration(
        String name,
        List<String> domains,
        DeploymentMode deploymentMode,
        String databaseConfigId,
        String planId) {
    public TenantRegistration {
        Objects.requireNonNull(name, "name must not be null");
        domains = TenantCommandValidation.normalizeDomains(domains);
        Objects.requireNonNull(deploymentMode, "deploymentMode must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        TenantCommandValidation.requireDatabaseConfigForDedicatedMode(deploymentMode, databaseConfigId);
    }
}
