package com.rafaga.backend.tenant.domain.model;

import java.time.Instant;

/** Public read model for a deployment target (shared pool or future dedicated instance). */
@org.springframework.modulith.NamedInterface("api")
public record DeploymentTargetView(
        String id,
        DeploymentTargetType targetType,
        String protocol,
        String privateOrigin,
        String healthPath,
        DeploymentTargetStatus status,
        DeploymentTargetCapacity capacity,
        Instant updatedAt) {
}
