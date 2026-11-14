package com.rafaga.backend.media.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAssetEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "upload_key", unique = true, length = 1024)
    private String uploadKey;

    @Column(name = "upload_expires_at")
    private Instant uploadExpiresAt;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Column(name = "object_key", unique = true, length = 1024)
    private String objectKey;

    @Column(name = "declared_content_type", nullable = false, length = 100)
    private String declaredContentType;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "asset_type", nullable = false, length = 16)
    private String assetType;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public MediaAssetEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUploadKey() { return uploadKey; }
    public void setUploadKey(String uploadKey) { this.uploadKey = uploadKey; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public void setUploadExpiresAt(Instant uploadExpiresAt) { this.uploadExpiresAt = uploadExpiresAt; }
    public long getReservedBytes() { return reservedBytes; }
    public void setReservedBytes(long reservedBytes) { this.reservedBytes = reservedBytes; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getDeclaredContentType() { return declaredContentType; }
    public void setDeclaredContentType(String declaredContentType) { this.declaredContentType = declaredContentType; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeleteRequestedAt() { return deleteRequestedAt; }
    public void setDeleteRequestedAt(Instant deleteRequestedAt) { this.deleteRequestedAt = deleteRequestedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
