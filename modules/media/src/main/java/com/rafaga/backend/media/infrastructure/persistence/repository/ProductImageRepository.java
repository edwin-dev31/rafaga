package com.rafaga.backend.media.infrastructure.persistence.repository;

import com.rafaga.backend.media.infrastructure.persistence.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, UUID> {

    List<ProductImageEntity> findByTenantIdAndProductId(String tenantId, UUID productId);

    List<ProductImageEntity> findByTenantIdAndProductIdOrderByPositionAsc(String tenantId, UUID productId);

    List<ProductImageEntity> findByTenantIdAndProductIdInOrderByProductIdAscPositionAsc(
            String tenantId, Collection<UUID> productIds);

    boolean existsByTenantIdAndMediaAssetId(String tenantId, UUID mediaAssetId);

    @Query("SELECT COUNT(pi) > 0 FROM ProductImageEntity pi WHERE pi.tenantId = :tenantId AND pi.mediaAssetId = :mediaAssetId")
    boolean existsByMediaAssetId(@Param("tenantId") String tenantId, @Param("mediaAssetId") UUID mediaAssetId);
}
