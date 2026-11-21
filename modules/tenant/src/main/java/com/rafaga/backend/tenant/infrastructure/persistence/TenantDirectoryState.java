package com.rafaga.backend.tenant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_directory_state")
public class TenantDirectoryState {
    @Id @Column(name = "singleton_id") private short singletonId;
    @Column(name = "current_version") private long currentVersion;
    @Column(name = "updated_at") private Instant updatedAt;
    protected TenantDirectoryState() { }
    public long getCurrentVersion() { return currentVersion; }
    public long advance(Instant now) { currentVersion++; updatedAt = now; return currentVersion; }
}
