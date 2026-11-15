package com.rafaga.backend.media.infrastructure.persistence.adapter;

import com.rafaga.backend.media.application.port.out.MediaRepository;
import com.rafaga.backend.media.domain.model.*;
import com.rafaga.backend.media.infrastructure.persistence.entity.MediaAssetEntity;
import com.rafaga.backend.media.infrastructure.persistence.entity.ProductImageEntity;
import com.rafaga.backend.media.infrastructure.persistence.entity.TenantMediaQuotaEntity;
import com.rafaga.backend.media.infrastructure.persistence.repository.MediaAssetRepository;
import com.rafaga.backend.media.infrastructure.persistence.repository.ProductImageRepository;
import com.rafaga.backend.media.infrastructure.persistence.repository.TenantMediaQuotaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MediaJpaAdapter implements MediaRepository {

    private final MediaAssetRepository assetRepo;
    private final ProductImageRepository productImageRepo;
    private final TenantMediaQuotaRepository quotaRepo;
    private final Clock clock;

    @Value("${media.quota.default-max-assets:500}")
    private long defaultMaxAssets;

    @Value("${media.quota.default-max-storage-bytes:5368709120}")
    private long defaultMaxStorageBytes;

    @Autowired
    public MediaJpaAdapter(MediaAssetRepository assetRepo,
                           ProductImageRepository productImageRepo,
                           TenantMediaQuotaRepository quotaRepo) {
        this(assetRepo, productImageRepo, quotaRepo, Clock.systemUTC());
    }

    MediaJpaAdapter(MediaAssetRepository assetRepo,
                    ProductImageRepository productImageRepo,
                    TenantMediaQuotaRepository quotaRepo,
                    Clock clock) {
        this.assetRepo = assetRepo;
        this.productImageRepo = productImageRepo;
        this.quotaRepo = quotaRepo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MediaAsset save(MediaAsset asset) {
        MediaAssetEntity entity = toJpa(asset);
        entity = assetRepo.save(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findById(UUID id, String tenantId) {
        return assetRepo.findById(id)
            .filter(e -> e.getTenantId().equals(tenantId))
            .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findByTenantAndStatus(String tenantId, AssetStatus status) {
        return assetRepo.findByTenantIdAndStatus(tenantId, status.name()).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findPendingExpired(Instant threshold) {
        return assetRepo.findByStatusAndUploadExpiresAtBefore("PENDING", threshold).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findPendingDeletePastGrace(Instant graceThreshold) {
        return assetRepo.findByStatusAndDeleteRequestedAtBefore("PENDING_DELETE", graceThreshold).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public boolean failPendingAsset(UUID id, String tenantId, String failureReason) {
        MediaAssetEntity asset = assetRepo.findById(id)
                .filter(entity -> entity.getTenantId().equals(tenantId))
                .orElse(null);
        if (asset == null) {
            return false;
        }
        Instant now = Instant.now(clock);
        if (assetRepo.failPendingAsset(id, tenantId, failureReason, now) == 0) {
            return false;
        }
        assetRepo.releaseQuotaReservation(tenantId, asset.getReservedBytes(), now);
        return true;
    }

    @Override
    @Transactional
    public TenantMediaQuota getQuota(String tenantId) {
        return toQuota(quotaEntity(tenantId));
    }

    @Override
    @Transactional
    public TenantMediaQuota updateQuotaLimits(
            String tenantId, long maxAssets, long maxStorageBytes) {
        TenantMediaQuotaEntity entity = quotaEntity(tenantId);
        entity.setMaxAssets(maxAssets);
        entity.setMaxStorageBytes(maxStorageBytes);
        entity.setUpdatedAt(Instant.now(clock));
        return toQuota(quotaRepo.save(entity));
    }

    @Override
    @Transactional
    public boolean reserveQuota(String tenantId, long bytes) {
        TenantMediaQuotaEntity entity = quotaEntity(tenantId);
        Instant now = Instant.now(clock);
        int updated = assetRepo.reserveQuota(tenantId, bytes, entity.getMaxAssets(),
            entity.getMaxStorageBytes(), now);
        return updated > 0;
    }

    @Override
    @Transactional
    public void commitQuotaUsage(String tenantId, long reservedBytes, long actualBytes) {
        assetRepo.commitQuotaUsage(tenantId, reservedBytes, actualBytes, Instant.now(clock));
    }

    @Override
    @Transactional
    public ProductImage saveProductImage(ProductImage productImage) {
        ProductImageEntity entity = toProductImageJpa(productImage);
        entity = productImageRepo.save(entity);
        return toProductImageDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductImage> findProductImage(UUID id, String tenantId) {
        return productImageRepo.findById(id)
            .filter(e -> e.getTenantId().equals(tenantId))
            .map(this::toProductImageDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImage> findByProductId(UUID productId, String tenantId) {
        return productImageRepo.findByTenantIdAndProductId(tenantId, productId).stream()
            .map(this::toProductImageDomain)
            .toList();
    }

    @Override
    @Transactional
    public void deleteProductImage(UUID id, String tenantId) {
        productImageRepo.findById(id)
            .filter(e -> e.getTenantId().equals(tenantId))
            .ifPresent(productImageRepo::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByMediaAssetId(UUID mediaAssetId, String tenantId) {
        return productImageRepo.existsByMediaAssetId(tenantId, mediaAssetId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImage> findByProductIdOrderByPosition(UUID productId, String tenantId) {
        return productImageRepo.findByTenantIdAndProductIdOrderByPositionAsc(tenantId, productId).stream()
            .map(this::toProductImageDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImage> findByProductIdsOrderByProductIdAndPosition(
            Collection<UUID> productIds, String tenantId) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productImageRepo
                .findByTenantIdAndProductIdInOrderByProductIdAscPositionAsc(tenantId, productIds)
                .stream()
                .map(this::toProductImageDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MediaAsset> findByIds(Collection<UUID> ids, String tenantId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assetRepo.findByTenantIdAndIdIn(tenantId, ids).stream()
                .map(this::toDomain)
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
    }

    @Override
    @Transactional
    public void updateProductImagePositions(List<ProductImage> images) {
        for (ProductImage pi : images) {
            productImageRepo.findById(pi.getId()).ifPresent(entity -> {
                entity.setPosition(pi.getPosition());
                entity.setPrimary(pi.isPrimary());
                productImageRepo.save(entity);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findActiveBranding(String tenantId, AssetType assetType) {
        return assetRepo.findActiveBranding(tenantId, assetType.name()).stream()
            .findFirst()
            .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> findReadyWithStagingKey() {
        return assetRepo.findReadyWithStagingKey().stream()
            .map(this::toDomain)
            .toList();
    }

    // ---- Mapping helpers ----

    private MediaAssetEntity toJpa(MediaAsset asset) {
        MediaAssetEntity entity = new MediaAssetEntity();
        entity.setId(asset.getId());
        entity.setTenantId(asset.getTenantId());
        entity.setUploadKey(asset.getUploadKey());
        entity.setUploadExpiresAt(asset.getUploadExpiresAt());
        entity.setReservedBytes(asset.getReservedBytes());
        entity.setObjectKey(asset.getObjectKey());
        entity.setDeclaredContentType(asset.getDeclaredContentType());
        entity.setContentType(asset.getContentType());
        entity.setSizeBytes(asset.getSizeBytes());
        entity.setWidth(asset.getWidth());
        entity.setHeight(asset.getHeight());
        entity.setStatus(asset.getStatus().name());
        entity.setAssetType(asset.getAssetType().name());
        entity.setFailureCode(asset.getFailureCode());
        entity.setFailureReason(asset.getFailureReason());
        entity.setCreatedAt(asset.getCreatedAt());
        entity.setUpdatedAt(asset.getUpdatedAt());
        entity.setDeleteRequestedAt(asset.getDeleteRequestedAt());
        entity.setDeletedAt(asset.getDeletedAt());
        return entity;
    }

    private MediaAsset toDomain(MediaAssetEntity entity) {
        return MediaAsset.fromDb(
            entity.getId(),
            entity.getTenantId(),
            entity.getUploadKey(),
            entity.getUploadExpiresAt(),
            entity.getReservedBytes(),
            entity.getDeclaredContentType(),
            AssetType.valueOf(entity.getAssetType()),
            entity.getCreatedAt(),
            entity.getObjectKey(),
            entity.getContentType(),
            entity.getSizeBytes(),
            entity.getWidth(),
            entity.getHeight(),
            AssetStatus.valueOf(entity.getStatus()),
            entity.getFailureCode(),
            entity.getFailureReason(),
            entity.getUpdatedAt(),
            entity.getDeleteRequestedAt(),
            entity.getDeletedAt()
        );
    }

    private TenantMediaQuotaEntity quotaEntity(String tenantId) {
        TenantMediaQuotaEntity entity = quotaRepo.findByTenantId(tenantId);
        if (entity != null) {
            return entity;
        }
        entity = new TenantMediaQuotaEntity();
        entity.setTenantId(tenantId);
        entity.setMaxAssets(defaultMaxAssets);
        entity.setMaxStorageBytes(defaultMaxStorageBytes);
        entity.setUsedAssets(0);
        entity.setUsedStorageBytes(0);
        entity.setReservedAssets(0);
        entity.setReservedBytes(0);
        entity.setUpdatedAt(Instant.now(clock));
        return quotaRepo.save(entity);
    }

    private TenantMediaQuota toQuota(TenantMediaQuotaEntity entity) {
        return new TenantMediaQuota(
                entity.getTenantId(),
                entity.getMaxAssets(),
                entity.getMaxStorageBytes(),
                entity.getUsedAssets(),
                entity.getUsedStorageBytes(),
                entity.getReservedAssets(),
                entity.getReservedBytes());
    }

    private ProductImageEntity toProductImageJpa(ProductImage pi) {
        ProductImageEntity entity = new ProductImageEntity();
        entity.setId(pi.getId());
        entity.setTenantId(pi.getTenantId());
        entity.setProductId(pi.getProductId());
        entity.setMediaAssetId(pi.getMediaAssetId());
        entity.setPosition(pi.getPosition());
        entity.setPrimary(pi.isPrimary());
        entity.setCreatedAt(pi.getCreatedAt());
        return entity;
    }

    private ProductImage toProductImageDomain(ProductImageEntity entity) {
        return new ProductImage(
            entity.getId(),
            entity.getTenantId(),
            entity.getProductId(),
            entity.getMediaAssetId(),
            entity.getPosition(),
            entity.isPrimary(),
            entity.getCreatedAt()
        );
    }
}
