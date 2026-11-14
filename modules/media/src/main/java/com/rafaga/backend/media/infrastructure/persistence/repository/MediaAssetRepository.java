package com.rafaga.backend.media.infrastructure.persistence.repository;

import com.rafaga.backend.media.infrastructure.persistence.entity.MediaAssetEntity;
import com.rafaga.backend.media.infrastructure.persistence.entity.TenantMediaQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

    List<MediaAssetEntity> findByTenantIdAndStatus(String tenantId, String status);

    List<MediaAssetEntity> findByTenantIdAndIdIn(String tenantId, Collection<UUID> ids);

    List<MediaAssetEntity> findByStatusAndUploadExpiresAtBefore(String status, Instant threshold);

    List<MediaAssetEntity> findByStatusAndDeleteRequestedAtBefore(String status, Instant threshold);

    @Query("SELECT a FROM MediaAssetEntity a WHERE a.tenantId = :tenantId AND a.assetType = :assetType AND a.status = 'READY'")
    List<MediaAssetEntity> findActiveBranding(@Param("tenantId") String tenantId, @Param("assetType") String assetType);

    @Modifying
    @Query("UPDATE TenantMediaQuotaEntity q SET q.reservedAssets = q.reservedAssets + 1, " +
           "q.reservedBytes = q.reservedBytes + :bytes, q.updatedAt = :now " +
           "WHERE q.tenantId = :tenantId AND " +
           "(q.usedAssets + q.reservedAssets) < :maxAssets AND " +
           "(q.usedStorageBytes + q.reservedBytes + :bytes) <= :maxStorageBytes")
    int reserveQuota(@Param("tenantId") String tenantId,
                     @Param("bytes") long bytes,
                     @Param("maxAssets") long maxAssets,
                     @Param("maxStorageBytes") long maxStorageBytes,
                     @Param("now") Instant now);

    /**
     * Release quota reservation atomically, but only if there is at least one
     * PENDING asset for the tenant. This prevents double-release: if the asset
     * was already committed (READY), the reservation has been consumed by
     * commitQuotaUsage and should not be released again.
     */
    @Modifying
    @Query("UPDATE TenantMediaQuotaEntity q SET " +
           "q.reservedAssets = GREATEST(q.reservedAssets - 1, 0), " +
           "q.reservedBytes = GREATEST(q.reservedBytes - :bytes, 0), " +
           "q.updatedAt = :now " +
           "WHERE q.tenantId = :tenantId " +
           "AND q.reservedAssets > 0 " +
           "AND EXISTS (SELECT 1 FROM MediaAssetEntity a " +
           "WHERE a.tenantId = :tenantId AND a.status = 'PENDING')")
    void releaseQuotaReservation(@Param("tenantId") String tenantId,
                                 @Param("bytes") long bytes,
                                 @Param("now") Instant now);

    @Modifying
    @Query("UPDATE TenantMediaQuotaEntity q SET " +
           "q.usedAssets = q.usedAssets + 1, " +
           "q.usedStorageBytes = q.usedStorageBytes + :actualBytes, " +
           "q.reservedAssets = GREATEST(q.reservedAssets - 1, 0), " +
           "q.reservedBytes = GREATEST(q.reservedBytes - :reservedBytes, 0), " +
           "q.updatedAt = :now " +
           "WHERE q.tenantId = :tenantId")
    void commitQuotaUsage(@Param("tenantId") String tenantId,
                          @Param("reservedBytes") long reservedBytes,
                          @Param("actualBytes") long actualBytes,
                          @Param("now") Instant now);

    @Query("SELECT a FROM MediaAssetEntity a WHERE a.status = 'READY' AND a.uploadKey IS NOT NULL")
    List<MediaAssetEntity> findReadyWithStagingKey();
}
