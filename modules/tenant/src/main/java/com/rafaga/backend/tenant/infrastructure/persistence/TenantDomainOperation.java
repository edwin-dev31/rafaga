package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.DomainOperationState;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenant_domain_operations", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "domain", "operation_type", "idempotency_key"}))
public class TenantDomainOperation {
    @Id private String id;
    @Column(name = "tenant_id") private String tenantId;
    private String domain;
    @Column(name = "operation_type") private String type;
    @Column(name = "idempotency_key") private String key;
    @Enumerated(EnumType.STRING) @Column(name = "operation_state") private DomainOperationState state;
    @Column(name = "requested_at") private Instant requestedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "directory_version") private Long directoryVersion;

    protected TenantDomainOperation() { }

    public TenantDomainOperation(String id, String tenantId, String domain, String type, String key, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.domain = domain;
        this.type = type;
        this.key = key;
        this.state = DomainOperationState.PENDING;
        this.requestedAt = now;
    }

    public void complete(long version, Instant now) {
        this.state = DomainOperationState.SUCCEEDED;
        this.directoryVersion = version;
        this.completedAt = now;
    }

    public void fail(String code, Instant now) {
        this.state = DomainOperationState.FAILED;
        this.failureCode = code;
        this.completedAt = now;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getDomain() { return domain; }
    public String getType() { return type; }
    public String getKey() { return key; }
    public DomainOperationState getState() { return state; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureCode() { return failureCode; }
    public Long getDirectoryVersion() { return directoryVersion; }
}
