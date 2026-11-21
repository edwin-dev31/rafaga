package com.rafaga.backend.tenant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "r2_cors_revisions")
public class R2CorsRevisionEntity {
    @Id
    private long version;

    @Column(name = "origins_json", nullable = false)
    private String originsJson;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "rollback_of_version")
    private Long rollbackOfVersion;

    @Column(nullable = false, length = 32)
    private String status;

    protected R2CorsRevisionEntity() { }

    public R2CorsRevisionEntity(long version, String originsJson, Instant appliedAt, Long rollbackOfVersion, String status) {
        this.version = version;
        this.originsJson = originsJson;
        this.appliedAt = appliedAt;
        this.rollbackOfVersion = rollbackOfVersion;
        this.status = status;
    }

    public long getVersion() { return version; }
    public String getOriginsJson() { return originsJson; }
    public Instant getAppliedAt() { return appliedAt; }
    public Long getRollbackOfVersion() { return rollbackOfVersion; }
    public String getStatus() { return status; }
}
