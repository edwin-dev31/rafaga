package com.rafaga.backend.media.domain.model;

import java.time.Instant;
import java.util.UUID;

public class MediaAsset {
    private final UUID id;
    private final String tenantId;
    private String uploadKey;
    private Instant uploadExpiresAt;
    private long reservedBytes;
    private String objectKey;
    private final String declaredContentType;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private AssetStatus status;
    private final AssetType assetType;
    private String failureCode;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deleteRequestedAt;
    private Instant deletedAt;

    public MediaAsset(UUID id, String tenantId, String uploadKey, Instant uploadExpiresAt,
                      long reservedBytes, String declaredContentType, AssetType assetType, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.uploadKey = uploadKey;
        this.uploadExpiresAt = uploadExpiresAt;
        this.reservedBytes = reservedBytes;
        this.declaredContentType = declaredContentType;
        this.assetType = assetType;
        this.status = AssetStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Factory for rehydrating from database
    public static MediaAsset fromDb(UUID id, String tenantId, String uploadKey, Instant uploadExpiresAt,
                      long reservedBytes, String declaredContentType, AssetType assetType, Instant createdAt,
                      String objectKey, String contentType, Long sizeBytes, Integer width, Integer height,
                      AssetStatus status, String failureCode, String failureReason, Instant updatedAt,
                      Instant deleteRequestedAt, Instant deletedAt) {
        MediaAsset asset = new MediaAsset(id, tenantId, uploadKey, uploadExpiresAt,
            reservedBytes, declaredContentType, assetType, createdAt);
        asset.objectKey = objectKey;
        asset.contentType = contentType;
        asset.sizeBytes = sizeBytes;
        asset.width = width;
        asset.height = height;
        asset.status = status;
        asset.failureCode = failureCode;
        asset.failureReason = failureReason;
        asset.updatedAt = updatedAt;
        asset.deleteRequestedAt = deleteRequestedAt;
        asset.deletedAt = deletedAt;
        return asset;
    }

    public void markReady(String finalObjectKey, int width, int height, long sizeBytes, Instant now) {
        transitionTo(AssetStatus.READY);
        this.objectKey = finalObjectKey;
        this.contentType = "image/webp";
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
        this.updatedAt = now;
    }

    public void markFailed(String code, String reason, Instant now) {
        transitionTo(AssetStatus.FAILED);
        this.failureCode = code;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void markPendingDelete(Instant requestedAt) {
        transitionTo(AssetStatus.PENDING_DELETE);
        this.deleteRequestedAt = requestedAt;
        this.updatedAt = requestedAt;
    }

    public void markDeleted(Instant deletedAt) {
        transitionTo(AssetStatus.DELETED);
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }

    public void cancelDeletion(Instant now) {
        transitionTo(AssetStatus.READY);
        this.deleteRequestedAt = null;
        this.updatedAt = now;
    }

    /**
     * Clear the uploadKey (staging key) after it has been deleted from R2.
     * Used by the orphaned-staging cleanup job.
     */
    public void clearUploadKey() {
        this.uploadKey = null;
        this.uploadExpiresAt = null;
    }

    private void transitionTo(AssetStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidAssetStateTransitionException(status, next);
        }
        this.status = next;
    }

    // Getters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUploadKey() { return uploadKey; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public long getReservedBytes() { return reservedBytes; }
    public String getObjectKey() { return objectKey; }
    public String getDeclaredContentType() { return declaredContentType; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public AssetStatus getStatus() { return status; }
    public AssetType getAssetType() { return assetType; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeleteRequestedAt() { return deleteRequestedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
