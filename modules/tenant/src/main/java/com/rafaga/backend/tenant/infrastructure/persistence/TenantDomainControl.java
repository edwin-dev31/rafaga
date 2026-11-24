package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.DomainOperationState;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_domains")
@IdClass(TenantDomainControlId.class)
public class TenantDomainControl {
    @Id @Column(name = "tenant_id") private String tenantId;
    @Id @Column(name = "domain") private String domain;
    @Enumerated(EnumType.STRING) @Column(name = "domain_status") private DomainStatus status;
    @Column(name = "frontend_origin") private String frontendOrigin;
    @Column(name = "target_id") private String targetId;
    @Column(name = "directory_version") private long directoryVersion;
    @Column(name = "requested_at") private Instant requestedAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "suspended_at") private Instant suspendedAt;
    @Enumerated(EnumType.STRING) @Column(name = "operation_state") private DomainOperationState operationState;
    @Column(name = "operation_updated_at") private Instant operationUpdatedAt;
    @Column(name = "external_hostname_id") private String externalHostnameId;
    @Column(name = "retry_count") private int retryCount;
    @Column(name = "next_retry_at") private Instant nextRetryAt;
    @Column(name = "last_failure_code") private String lastFailureCode;
    @Column(name = "ownership_verification_name") private String ownershipVerificationName;
    @Column(name = "ownership_verification_value") private String ownershipVerificationValue;

    protected TenantDomainControl() { }
    public TenantDomainControl(String tenantId, String domain, DomainStatus status, String frontendOrigin, String targetId, Instant now) {
        this.tenantId = tenantId;
        this.domain = domain;
        this.status = status;
        this.frontendOrigin = frontendOrigin;
        this.targetId = targetId;
        this.requestedAt = now;
        this.operationState = status == DomainStatus.ACTIVE ? DomainOperationState.SUCCEEDED : DomainOperationState.IDLE;
        this.operationUpdatedAt = now;
        if (status == DomainStatus.ACTIVE) {
            this.activatedAt = now;
        }
    }

    public void transition(DomainStatus next, long version, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid domain transition " + status + " -> " + next);
        }
        status = next;
        directoryVersion = version;
        operationUpdatedAt = now;
        operationState = switch (next) {
            case FAILED -> DomainOperationState.FAILED;
            case REQUESTED -> DomainOperationState.RETRY_SCHEDULED;
            case OWNERSHIP_PENDING, CERTIFICATE_PENDING, TARGET_PENDING -> DomainOperationState.RUNNING;
            case ACTIVE, SUSPENDED, REMOVING -> DomainOperationState.SUCCEEDED;
        };
        if (next == DomainStatus.CERTIFICATE_PENDING && verifiedAt == null) {
            verifiedAt = now;
        }
        if (next == DomainStatus.ACTIVE) {
            if (verifiedAt == null) {
                verifiedAt = now;
            }
            activatedAt = now;
        }
        if (next == DomainStatus.SUSPENDED) {
            suspendedAt = now;
        }
    }

    public void configure(String newFrontendOrigin, String newTargetId, long version, Instant now) {
        frontendOrigin = newFrontendOrigin;
        targetId = newTargetId;
        directoryVersion = version;
        operationUpdatedAt = now;
    }

    public void recordExternalHostnameId(String externalId, Instant now) {
        this.externalHostnameId = externalId;
        this.operationUpdatedAt = now;
    }

    public void recordOwnershipVerification(String name, String value, Instant now) {
        if (name != null && !name.isBlank()) {
            this.ownershipVerificationName = name;
        }
        if (value != null && !value.isBlank()) {
            this.ownershipVerificationValue = value;
        }
        this.operationUpdatedAt = now;
    }

    public void scheduleRetry(String failureCode, Instant retryAt, Instant now) {
        this.retryCount++;
        this.nextRetryAt = retryAt;
        this.lastFailureCode = failureCode;
        this.operationState = DomainOperationState.RETRY_SCHEDULED;
        this.operationUpdatedAt = now;
    }

    public void clearRetry(Instant now) {
        this.retryCount = 0;
        this.nextRetryAt = null;
        this.lastFailureCode = null;
        this.operationUpdatedAt = now;
    }

    public void markFailed(String failureCode, long version, Instant now) {
        transition(DomainStatus.FAILED, version, now);
        this.lastFailureCode = failureCode;
        this.nextRetryAt = null;
    }

    public String getTenantId() { return tenantId; }
    public String getDomain() { return domain; }
    public DomainStatus getStatus() { return status; }
    public String getFrontendOrigin() { return frontendOrigin; }
    public String getTargetId() { return targetId; }
    public long getDirectoryVersion() { return directoryVersion; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public DomainOperationState getOperationState() { return operationState; }
    public Instant getOperationUpdatedAt() { return operationUpdatedAt; }
    public String getExternalHostnameId() { return externalHostnameId; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getLastFailureCode() { return lastFailureCode; }
    public String getOwnershipVerificationName() { return ownershipVerificationName; }
    public String getOwnershipVerificationValue() { return ownershipVerificationValue; }
}
