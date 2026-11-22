package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.TenantTargetTransitionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_target_transitions")
public class TenantTargetTransitionEntity {
    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String domain;

    @Column(name = "from_target_id")
    private String fromTargetId;

    @Column(name = "to_target_id", nullable = false)
    private String toTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_state", nullable = false)
    private TenantTargetTransitionState state;

    @Column(name = "data_validation_checksum")
    private String dataValidationChecksum;

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TenantTargetTransitionEntity() { }

    public TenantTargetTransitionEntity(
            String id,
            String tenantId,
            String domain,
            String fromTargetId,
            String toTargetId,
            String initiatedBy,
            Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.domain = domain;
        this.fromTargetId = fromTargetId;
        this.toTargetId = toTargetId;
        this.state = TenantTargetTransitionState.PREPARED;
        this.initiatedBy = initiatedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markValidated(String checksum, Instant now) {
        requireTransition(TenantTargetTransitionState.VALIDATED);
        this.state = TenantTargetTransitionState.VALIDATED;
        this.dataValidationChecksum = checksum;
        this.updatedAt = now;
    }

    public void markActivated(Instant now) {
        requireTransition(TenantTargetTransitionState.ACTIVATED);
        this.state = TenantTargetTransitionState.ACTIVATED;
        this.updatedAt = now;
    }

    public void markDrained(Instant now) {
        requireTransition(TenantTargetTransitionState.DRAINED);
        this.state = TenantTargetTransitionState.DRAINED;
        this.updatedAt = now;
        this.completedAt = now;
    }

    public void markRolledBack(String reason, Instant now) {
        requireTransition(TenantTargetTransitionState.ROLLED_BACK);
        this.state = TenantTargetTransitionState.ROLLED_BACK;
        this.failureReason = reason;
        this.updatedAt = now;
        this.completedAt = now;
    }

    private void requireTransition(TenantTargetTransitionState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid target transition " + state + " -> " + next);
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getDomain() { return domain; }
    public String getFromTargetId() { return fromTargetId; }
    public String getToTargetId() { return toTargetId; }
    public TenantTargetTransitionState getState() { return state; }
    public String getDataValidationChecksum() { return dataValidationChecksum; }
    public String getInitiatedBy() { return initiatedBy; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
