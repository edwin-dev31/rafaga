package com.rafaga.backend.media.application.port.out;

import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.AssetType;
import com.rafaga.backend.media.domain.model.MediaAsset;
import com.rafaga.backend.media.domain.model.ProductImage;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {
    MediaAsset save(MediaAsset asset);
    Optional<MediaAsset> findById(UUID id, String tenantId);
    List<MediaAsset> findByTenantAndStatus(String tenantId, AssetStatus status);
    List<MediaAsset> findPendingExpired(Instant threshold);
    List<MediaAsset> findPendingDeletePastGrace(Instant graceThreshold);
    void updateStatus(UUID id, AssetStatus status, String failureReason);
    TenantMediaQuota getQuota(String tenantId);
    boolean reserveQuota(String tenantId, long bytes);
    void releaseQuotaReservation(String tenantId, long bytes);
    void commitQuotaUsage(String tenantId, long reservedBytes, long actualBytes);
    ProductImage saveProductImage(ProductImage productImage);
    Optional<ProductImage> findProductImage(UUID id, String tenantId);
    List<ProductImage> findByProductId(UUID productId, String tenantId);
    void deleteProductImage(UUID id, String tenantId);
    boolean existsByMediaAssetId(UUID mediaAssetId, String tenantId);
    List<ProductImage> findByProductIdOrderByPosition(UUID productId, String tenantId);
    List<ProductImage> findByProductIdsOrderByProductIdAndPosition(
            Collection<UUID> productIds, String tenantId);
    Map<UUID, MediaAsset> findByIds(Collection<UUID> ids, String tenantId);
    void updateProductImagePositions(List<ProductImage> images);
    Optional<MediaAsset> findActiveBranding(String tenantId, AssetType assetType);
    /**
     * Find assets that are READY but still have a non-null uploadKey (staging key).
     * These are orphaned staging objects that should be deleted from R2.
     */
    List<MediaAsset> findReadyWithStagingKey();
}
