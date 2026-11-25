package com.rafaga.backend.tenant.application.port;

import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantStatus;

public record TenantSearchFilter(
        String query,
        TenantStatus status,
        String planId,
        DeploymentMode deploymentMode) {
    public static TenantSearchFilter empty() {
        return new TenantSearchFilter(null, null, null, null);
    }
}
