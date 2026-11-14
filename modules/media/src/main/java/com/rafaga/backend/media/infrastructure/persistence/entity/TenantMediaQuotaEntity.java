package com.rafaga.backend.media.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenant_media_quotas")
public class TenantMediaQuotaEntity {

    @Id
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "max_assets", nullable = false)
    private long maxAssets;

    @Column(name = "max_storage_bytes", nullable = false)
    private long maxStorageBytes;

    @Column(name = "used_assets", nullable = false)
    private long usedAssets;

    @Column(name = "used_storage_bytes", nullable = false)
    private long usedStorageBytes;

    @Column(name = "reserved_assets", nullable = false)
    private long reservedAssets;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TenantMediaQuotaEntity() {}

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public long getMaxAssets() { return maxAssets; }
    public void setMaxAssets(long maxAssets) { this.maxAssets = maxAssets; }
    public long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
    public long getUsedAssets() { return usedAssets; }
    public void setUsedAssets(long usedAssets) { this.usedAssets = usedAssets; }
    public long getUsedStorageBytes() { return usedStorageBytes; }
    public void setUsedStorageBytes(long usedStorageBytes) { this.usedStorageBytes = usedStorageBytes; }
    public long getReservedAssets() { return reservedAssets; }
    public void setReservedAssets(long reservedAssets) { this.reservedAssets = reservedAssets; }
    public long getReservedBytes() { return reservedBytes; }
    public void setReservedBytes(long reservedBytes) { this.reservedBytes = reservedBytes; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
