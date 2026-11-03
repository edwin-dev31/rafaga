package com.rafaga.backend.tenant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "tenant_feature_flags",
        indexes = @Index(name = "idx_tenant_feature_flags_tenant_id", columnList = "tenant_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_feature_flags_tenant_feature",
                columnNames = {"tenant_id", "feature_name"}))
public class FeatureFlag {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "feature_name", nullable = false, length = 100, updatable = false)
    private String featureName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    protected FeatureFlag() {
    }

    public FeatureFlag(
            String id,
            String tenantId,
            String featureName,
            boolean enabled,
            Instant effectiveFrom,
            Instant effectiveUntil) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.featureName = Objects.requireNonNull(featureName, "featureName must not be null");
        this.enabled = enabled;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.effectiveUntil = effectiveUntil;
    }

    public boolean isEnabledAt(Instant instant) {
        return enabled && !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }

    public void update(boolean enabled, Instant effectiveFrom, Instant effectiveUntil) {
        this.enabled = enabled;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.effectiveUntil = effectiveUntil;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getFeatureName() {
        return featureName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveUntil() {
        return effectiveUntil;
    }
}
