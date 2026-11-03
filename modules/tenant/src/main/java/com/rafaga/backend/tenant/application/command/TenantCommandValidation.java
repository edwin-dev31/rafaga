package com.rafaga.backend.tenant.application.command;

import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class TenantCommandValidation {
    private TenantCommandValidation() {
    }

    static List<String> normalizeDomains(List<String> domains) {
        List<String> normalizedDomains = Objects.requireNonNull(domains, "domains must not be null")
                .stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .toList();
        if (normalizedDomains.isEmpty()) {
            throw new IllegalArgumentException("Tenant must have at least one domain");
        }
        if (normalizedDomains.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Tenant domains must not be blank");
        }
        return normalizedDomains;
    }

    static void requireDatabaseConfigForDedicatedMode(
            DeploymentMode deploymentMode,
            String databaseConfigId) {
        if (deploymentMode == DeploymentMode.DEDICATED_DB
                && (databaseConfigId == null || databaseConfigId.isBlank())) {
            throw new IllegalArgumentException("Dedicated database tenants require a databaseConfigId");
        }
    }
}
