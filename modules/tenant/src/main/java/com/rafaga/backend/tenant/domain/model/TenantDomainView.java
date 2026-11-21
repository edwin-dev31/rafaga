package com.rafaga.backend.tenant.domain.model;

import java.time.Instant;

/** Public read model for a tenant domain's control-plane lifecycle and routing state. */
@org.springframework.modulith.NamedInterface("api")
public record TenantDomainView(
        String tenantId,
        String hostname,
        DomainStatus status,
        String frontendOrigin,
        String targetId,
        DeploymentTargetType targetType,
        long directoryVersion,
        Instant requestedAt,
        Instant verifiedAt,
        Instant activatedAt,
        Instant suspendedAt,
        DomainOperationState operationState,
        Instant operationUpdatedAt) {
}
