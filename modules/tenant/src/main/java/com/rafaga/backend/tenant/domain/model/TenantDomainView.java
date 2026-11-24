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
        Instant operationUpdatedAt,
        String ownershipVerificationName,
        String ownershipVerificationValue,
        String failureReason,
        String nextAction) {
    public TenantDomainView(
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
        this(tenantId, hostname, status, frontendOrigin, targetId, targetType, directoryVersion,
                requestedAt, verifiedAt, activatedAt, suspendedAt, operationState, operationUpdatedAt,
                null, null, null, nextActionFor(status));
    }

    public static String nextActionFor(DomainStatus status) {
        if (status == null) {
            return "NONE";
        }
        return switch (status) {
            case REQUESTED -> "WAIT_FOR_PROVISIONING";
            case OWNERSHIP_PENDING -> "ADD_OWNERSHIP_TXT_RECORD";
            case CERTIFICATE_PENDING -> "WAIT_FOR_CERTIFICATE_ISSUANCE";
            case TARGET_PENDING -> "VERIFY_FRONTEND_ORIGIN_AND_TARGET";
            case FAILED -> "REVIEW_FAILURE_AND_RETRY";
            case ACTIVE, SUSPENDED, REMOVING -> "NONE";
        };
    }
}
