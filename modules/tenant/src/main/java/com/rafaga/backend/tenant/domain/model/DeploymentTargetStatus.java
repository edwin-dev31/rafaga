package com.rafaga.backend.tenant.domain.model;

public enum DeploymentTargetStatus {
    PREPARING,
    HEALTHY,
    UNHEALTHY,
    DRAINING,
    RETIRED;

    public boolean isRoutable() {
        return this == HEALTHY;
    }
}
