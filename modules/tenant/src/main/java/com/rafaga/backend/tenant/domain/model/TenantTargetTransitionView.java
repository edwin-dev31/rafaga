package com.rafaga.backend.tenant.domain.model;

import java.time.Instant;

/** Public audit view of a controlled tenant deployment-target transition. */
@org.springframework.modulith.NamedInterface("api")
public record TenantTargetTransitionView(
        String id,
        String tenantId,
        String domain,
        String fromTargetId,
        String toTargetId,
        TenantTargetTransitionState state,
        String dataValidationChecksum,
        String initiatedBy,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
