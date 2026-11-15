package com.rafaga.backend.media.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.dto.AssociateBatch;
import com.rafaga.backend.media.application.dto.ConfirmBatch;
import com.rafaga.backend.media.application.dto.MediaAssetDto;
import com.rafaga.backend.media.application.dto.MediaAdminSummary;
import com.rafaga.backend.media.application.dto.ProductImageDto;
import com.rafaga.backend.media.application.dto.UploadBatch;
import com.rafaga.backend.media.application.port.out.*;
import com.rafaga.backend.media.application.exception.MediaConflictException;
import com.rafaga.backend.media.application.exception.MediaNotFoundException;
import com.rafaga.backend.media.application.exception.MediaValidationException;
import com.rafaga.backend.media.domain.model.*;
import com.rafaga.backend.media.domain.service.ImageProcessor;
import com.rafaga.backend.media.domain.service.ProcessingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private final MediaRepository repository;
    private final ObjectStorage objectStorage;
    private final ImageProcessor imageProcessor;
    private final CatalogPort catalogPort;
    private final Clock clock;

    @Value("${media.upload.presign-expiry-minutes:5}")
    private int presignExpiryMinutes;

    @Value("${media.optimization.max-output-size-bytes:5242880}")
    private long maxOutputSizeBytes;

    @Value("${media.optimization.max-input-size-bytes:10485760}")
    private long maxInputSizeBytes;

    @Value("${media.quota.default-max-assets:500}")
    private long defaultMaxAssets;

    @Value("${media.quota.default-max-storage-bytes:5368709120}")
    private long defaultMaxStorageBytes;

    @Value("${media.r2.public-base-url:https://media.rafaga.com}")
    private String publicBaseUrl;

    @Autowired
    public MediaService(MediaRepository repository, ObjectStorage objectStorage,
                        ImageProcessor imageProcessor, CatalogPort catalogPort) {
        this(repository, objectStorage, imageProcessor, catalogPort, Clock.systemUTC());
    }

    MediaService(MediaRepository repository, ObjectStorage objectStorage,
                 ImageProcessor imageProcessor, CatalogPort catalogPort, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.imageProcessor = imageProcessor;
        this.catalogPort = catalogPort;
        this.clock = clock;
    }

    /**
     * Produces an administrative summary for the tenant already established in
     * {@link TenantContext}. Callers that cross tenants must set and restore
     * that context themselves so PostgreSQL RLS remains effective.
     */
    @Transactional(readOnly = true)
    public MediaAdminSummary getAdminSummary() {
        String tenantId = TenantContext.getCurrentTenantId();
        EnumMap<AssetStatus, Long> assetsByStatus = new EnumMap<>(AssetStatus.class);
        for (AssetStatus status : AssetStatus.values()) {
            assetsByStatus.put(status, 0L);
        }

        List<MediaAsset> failed = List.of();
        List<MediaAsset> pendingCleanup = List.of();
        for (AssetStatus status : AssetStatus.values()) {
            List<MediaAsset> assets = repository.findByTenantAndStatus(tenantId, status);
            assetsByStatus.put(status, (long) assets.size());
            if (status == AssetStatus.FAILED) {
                failed = assets;
            } else if (status == AssetStatus.PENDING_DELETE) {
                pendingCleanup = assets;
            }
        }

        Comparator<MediaAsset> newestFirst = Comparator.comparing(MediaAsset::getUpdatedAt).reversed();
        List<MediaAdminSummary.FailedAsset> recentFailures = failed.stream()
            .sorted(newestFirst)
            .limit(20)
            .map(asset -> new MediaAdminSummary.FailedAsset(
                asset.getId(), asset.getFailureCode(), asset.getFailureReason(), asset.getUpdatedAt()))
            .toList();
        List<MediaAdminSummary.PendingCleanupAsset> cleanupAssets = pendingCleanup.stream()
            .sorted(Comparator.comparing(MediaAsset::getDeleteRequestedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(20)
            .map(asset -> new MediaAdminSummary.PendingCleanupAsset(
                asset.getId(), asset.getDeleteRequestedAt()))
            .toList();

        return new MediaAdminSummary(repository.getQuota(tenantId),
            Map.copyOf(assetsByStatus), recentFailures, cleanupAssets);
    }

    // ---- Batch Upload Request ----

    @Transactional
    public UploadBatch.Response requestUploadBatch(List<UploadBatch.Request> items) {
        String tenantId = TenantContext.getCurrentTenantId();
        int size = items.size();
        if (size < 1 || size > 8) {
            throw new MediaValidationException("MEDIA_BATCH_SIZE_INVALID",
                "Batch size must be between 1 and 8, got " + size);
        }

        List<UploadBatch.ItemResponse> responses = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        for (UploadBatch.Request item : items) {
            if (!repository.reserveQuota(tenantId, maxOutputSizeBytes)) {
                responses.add(new UploadBatch.ItemResponse("REJECTED", null, null, null, null,
                    new UploadBatch.Error("MEDIA_QUOTA_EXCEEDED",
                        "La imagen excede la cuota disponible del tenant")));
                rejected++;
                continue;
            }

            UUID assetId = UUID.randomUUID();
            String ulid = UUID.randomUUID().toString().replace("-", "");
            String extension = extractExtension(item.contentType());
            String uploadKey = "tenants/" + tenantId + "/uploads/" + assetId + "/" + ulid + "." + extension;
            Instant expiresAt = Instant.now(clock).plus(Duration.ofMinutes(presignExpiryMinutes));

            MediaAsset asset = new MediaAsset(assetId, tenantId, uploadKey, expiresAt,
                maxOutputSizeBytes, item.contentType(), item.assetType(), Instant.now(clock));
            repository.save(asset);

            ObjectStorage.PresignedUpload presigned = objectStorage.createUploadUrl(uploadKey, item.contentType(),
                Duration.ofMinutes(presignExpiryMinutes));

            responses.add(new UploadBatch.ItemResponse("PENDING", assetId, uploadKey,
                presigned.uploadUrl(), presigned.expiresAt(), null));
            accepted++;
        }

        return new UploadBatch.Response(responses, new UploadBatch.Summary(size, accepted, rejected));
    }

    // ---- Batch Confirm ----

    @Transactional
    public ConfirmBatch.Response confirmUploadBatch(List<UUID> assetIds) {
        String tenantId = TenantContext.getCurrentTenantId();
        int size = assetIds.size();
        if (size < 1 || size > 8) {
            throw new MediaValidationException("MEDIA_BATCH_SIZE_INVALID",
                "Batch size must be between 1 and 8, got " + size);
        }

        // Deduplicate and enforce PENDING state — idempotent batch confirm
        List<UUID> uniqueIds = new ArrayList<>(assetIds.stream().distinct().toList());
        if (uniqueIds.size() < 1 || uniqueIds.size() > 8) {
            throw new MediaValidationException("MEDIA_BATCH_SIZE_INVALID",
                "Batch size must be between 1 and 8 after deduplication, got " + uniqueIds.size());
        }

        List<ConfirmBatch.ItemResponse> results = new ArrayList<>();
        int ready = 0;
        int failed = 0;

        for (UUID assetId : uniqueIds) {
            MediaAsset asset = repository.findById(assetId, tenantId)
                .orElseThrow(() -> new MediaNotFoundException("MediaAsset", assetId));

            // Idempotency guard: only PENDING assets may be confirmed
            if (asset.getStatus() != AssetStatus.PENDING) {
                results.add(new ConfirmBatch.ItemResponse(assetId, asset.getStatus().name(), null,
                    new ConfirmBatch.Error("MEDIA_NOT_PENDING",
                        "El asset ya está en estado " + asset.getStatus().name() + ", no se puede reconfirmar", null, null)));
                failed++;
                continue;
            }

            try {
                if (!objectStorage.exists(asset.getUploadKey())) {
                    repository.failPendingAsset(assetId, tenantId, "Staging object not found");
                    results.add(new ConfirmBatch.ItemResponse(assetId, "FAILED", null,
                        new ConfirmBatch.Error("MEDIA_STAGING_MISSING", "El objeto de staging no existe", null, null)));
                    failed++;
                    continue;
                }

                ObjectStorage.Metadata metadata = objectStorage.getMetadata(asset.getUploadKey());
                if (metadata.sizeBytes() > maxInputSizeBytes) {
                    objectStorage.delete(asset.getUploadKey());
                    repository.failPendingAsset(assetId, tenantId, "Input size exceeds limit");
                    results.add(new ConfirmBatch.ItemResponse(assetId, "FAILED", null,
                        new ConfirmBatch.Error("MEDIA_INPUT_TOO_LARGE",
                            "El archivo supera el límite de entrada de " + maxInputSizeBytes + " bytes", null, null)));
                    failed++;
                    continue;
                }

                // Declare outside try-with-resources so catch blocks can access it
                // for compensation if DB fails after uploading the final object.
                String finalObjectKey = null;
                try (InputStream stream = objectStorage.download(asset.getUploadKey())) {
                    ProcessingResult processingResult = imageProcessor.process(stream, asset.getDeclaredContentType());

                    if (!processingResult.success()) {
                        objectStorage.delete(asset.getUploadKey());
                        repository.failPendingAsset(assetId, tenantId, processingResult.failureReason());
                        results.add(new ConfirmBatch.ItemResponse(assetId, "FAILED", null,
                            new ConfirmBatch.Error(processingResult.failureCode(),
                                processingResult.failureReason(),
                                processingResult.sizeBytes(), maxOutputSizeBytes)));
                        failed++;
                        continue;
                    }

                    finalObjectKey = generateFinalObjectKey(asset);
                    // Upload final object BEFORE DB commit — if DB fails below,
                    // we compensate by deleting this key so R2 doesn't accumulate orphans.
                    objectStorage.upload(finalObjectKey, processingResult.optimizedBytes(), "image/webp");

                    asset.markReady(finalObjectKey, processingResult.width(), processingResult.height(),
                        processingResult.sizeBytes(), Instant.now(clock));
                    repository.save(asset);
                    repository.commitQuotaUsage(tenantId, asset.getReservedBytes(), processingResult.sizeBytes());

                    // Delete staging after quota is committed — if this fails,
                    // the staging object will be cleaned up by the orphan-staging job
                    try {
                        objectStorage.delete(asset.getUploadKey());
                    } catch (Exception deleteEx) {
                        // Non-fatal: staging will expire and be cleaned up asynchronously
                    }

                    String cdnUrl = publicBaseUrl + "/" + finalObjectKey;
                    MediaAssetDto dto = toDto(asset, cdnUrl);
                    results.add(new ConfirmBatch.ItemResponse(assetId, "READY", dto, null));
                    ready++;
                } catch (MediaValidationException e) {
                    // Compensate: if we already uploaded the final object, delete it
                    if (finalObjectKey != null) {
                        try {
                            objectStorage.delete(finalObjectKey);
                        } catch (Exception compEx) {
                            // Log but don't mask the original exception
                        }
                    }
                    throw e;
                } catch (Exception e) {
                    // Compensate: if we already uploaded the final object, delete it
                    // so R2 doesn't accumulate orphaned objects when DB fails.
                    if (finalObjectKey != null) {
                        try {
                            objectStorage.delete(finalObjectKey);
                        } catch (Exception compEx) {
                            // Log but don't mask the original exception
                        }
                    }
                    repository.failPendingAsset(assetId, tenantId, e.getMessage());
                    results.add(new ConfirmBatch.ItemResponse(assetId, "FAILED", null,
                        new ConfirmBatch.Error("MEDIA_PROCESSING_ERROR", e.getMessage(), null, null)));
                    failed++;
                }
            } catch (MediaValidationException e) {
                throw e;
            } catch (Exception e) {
                repository.failPendingAsset(assetId, tenantId, e.getMessage());
                results.add(new ConfirmBatch.ItemResponse(assetId, "FAILED", null,
                    new ConfirmBatch.Error("MEDIA_PROCESSING_ERROR", e.getMessage(), null, null)));
                failed++;
            }
        }

        String message = buildConfirmMessage(ready, failed, size);
        return new ConfirmBatch.Response(message, results, new ConfirmBatch.Summary(size, ready, failed));
    }

    // ---- Delete Asset ----

    @Transactional
    public void deleteAsset(UUID assetId) {
        String tenantId = TenantContext.getCurrentTenantId();
        MediaAsset asset = repository.findById(assetId, tenantId)
            .orElseThrow(() -> new MediaNotFoundException("MediaAsset", assetId));

        if (repository.existsByMediaAssetId(assetId, tenantId)) {
            throw new MediaConflictException("MEDIA_ASSET_IN_USE",
                "El asset está asociado a productos y no puede eliminarse");
        }

        asset.markPendingDelete(Instant.now(clock));
        repository.save(asset);
    }

    // ---- Cancel Deletion ----

    @Transactional
    public MediaAssetDto cancelDeletion(UUID assetId) {
        String tenantId = TenantContext.getCurrentTenantId();
        MediaAsset asset = repository.findById(assetId, tenantId)
            .orElseThrow(() -> new MediaNotFoundException("MediaAsset", assetId));

        asset.cancelDeletion(Instant.now(clock));
        repository.save(asset);

        String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
        return toDto(asset, cdnUrl);
    }

    // ---- Get Asset ----

    @Transactional(readOnly = true)
    public MediaAssetDto getAsset(UUID assetId) {
        String tenantId = TenantContext.getCurrentTenantId();
        MediaAsset asset = repository.findById(assetId, tenantId)
            .orElseThrow(() -> new MediaNotFoundException("MediaAsset", assetId));

        String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
        return toDto(asset, cdnUrl);
    }

    // ---- List Assets ----

    @Transactional(readOnly = true)
    public List<MediaAssetDto> listAssets(AssetStatus status) {
        String tenantId = TenantContext.getCurrentTenantId();
        List<MediaAsset> assets;
        if (status != null) {
            assets = repository.findByTenantAndStatus(tenantId, status);
        } else {
            assets = new ArrayList<>();
            for (AssetStatus s : AssetStatus.values()) {
                assets.addAll(repository.findByTenantAndStatus(tenantId, s));
            }
        }
        return assets.stream().map(asset -> {
            String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
            return toDto(asset, cdnUrl);
        }).collect(Collectors.toList());
    }

    // ---- Associate to Product ----

    @Transactional
    public ProductImageDto associateToProduct(UUID assetId, UUID productId, int position, boolean isPrimary) {
        String tenantId = TenantContext.getCurrentTenantId();

        MediaAsset asset = repository.findById(assetId, tenantId)
            .orElseThrow(() -> new MediaNotFoundException("MediaAsset", assetId));

        if (asset.getStatus() != AssetStatus.READY) {
            throw new MediaValidationException("MEDIA_ASSET_NOT_READY",
                "La imagen no está lista para publicarse");
        }

        if (!catalogPort.productExists(productId, tenantId)) {
            throw new MediaValidationException("PRODUCT_NOT_FOUND",
                "El producto no existe o no pertenece al tenant");
        }

        UUID imageId = UUID.randomUUID();
        ProductImage productImage = new ProductImage(imageId, tenantId, productId, assetId,
            position, isPrimary, Instant.now(clock));
        repository.saveProductImage(productImage);

        return toProductImageDto(productImage, asset);
    }

    // ---- Batch Associate ----

    @Transactional
    public AssociateBatch.Response associateToProductBatch(UUID productId, List<AssociateBatch.Request> items) {
        String tenantId = TenantContext.getCurrentTenantId();
        int size = items.size();
        if (size < 1 || size > 8) {
            throw new MediaValidationException("MEDIA_BATCH_SIZE_INVALID",
                "Batch size must be between 1 and 8, got " + size);
        }

        long primaryCount = items.stream().filter(AssociateBatch.Request::isPrimary).count();
        if (primaryCount > 1) {
            throw new MediaValidationException("MEDIA_MULTIPLE_PRIMARY",
                "Solo una imagen puede ser primaria en el lote");
        }

        if (!catalogPort.productExists(productId, tenantId)) {
            throw new MediaValidationException("PRODUCT_NOT_FOUND",
                "El producto no existe o no pertenece al tenant");
        }

        List<AssociateBatch.ItemResponse> results = new ArrayList<>();
        int associated = 0;
        int rejected = 0;

        for (AssociateBatch.Request item : items) {
            try {
                MediaAsset asset = repository.findById(item.assetId(), tenantId)
                    .orElseThrow(() -> new MediaNotFoundException("MediaAsset", item.assetId()));

                if (asset.getStatus() != AssetStatus.READY) {
                    results.add(new AssociateBatch.ItemResponse(item.assetId(), "REJECTED", null,
                        new AssociateBatch.Error("MEDIA_ASSET_NOT_READY", "La imagen no está lista para publicarse")));
                    rejected++;
                    continue;
                }

                UUID imageId = UUID.randomUUID();
                ProductImage productImage = new ProductImage(imageId, tenantId, productId,
                    item.assetId(), item.position(), item.isPrimary(), Instant.now(clock));
                repository.saveProductImage(productImage);
                results.add(new AssociateBatch.ItemResponse(item.assetId(), "ASSOCIATED", imageId, null));
                associated++;
            } catch (MediaNotFoundException e) {
                results.add(new AssociateBatch.ItemResponse(item.assetId(), "REJECTED", null,
                    new AssociateBatch.Error("MEDIA_ASSET_NOT_FOUND", "El asset no existe")));
                rejected++;
            }
        }

        String message = buildAssociateMessage(associated, rejected, size);
        return new AssociateBatch.Response(message, results, new AssociateBatch.Summary(size, associated, rejected));
    }

    // ---- Dissociate ----

    @Transactional
    public void dissociateFromProduct(UUID productImageId) {
        String tenantId = TenantContext.getCurrentTenantId();
        repository.deleteProductImage(productImageId, tenantId);
    }

    // ---- List Product Images ----

    @Transactional(readOnly = true)
    public List<ProductImageDto> listProductImages(UUID productId) {
        String tenantId = TenantContext.getCurrentTenantId();
        List<ProductImage> images = repository.findByProductIdOrderByPosition(productId, tenantId);
        return images.stream().map(pi -> {
            MediaAsset asset = repository.findById(pi.getMediaAssetId(), tenantId).orElse(null);
            if (asset == null) return null;
            String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
            return toProductImageDto(pi, asset);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Loads public image data for a page of products in two repository queries.
     * The result preserves the image position within each product.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<ProductImageDto>> listProductImagesByProductIds(
            Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        String tenantId = TenantContext.getCurrentTenantId();
        List<ProductImage> images = repository
                .findByProductIdsOrderByProductIdAndPosition(productIds, tenantId);
        Map<UUID, MediaAsset> assetsById = repository.findByIds(images.stream()
                .map(ProductImage::getMediaAssetId)
                .collect(Collectors.toSet()), tenantId);

        Map<UUID, List<ProductImageDto>> imagesByProduct = new LinkedHashMap<>();
        for (ProductImage image : images) {
            MediaAsset asset = assetsById.get(image.getMediaAssetId());
            if (asset == null || asset.getStatus() != AssetStatus.READY
                    || asset.getObjectKey() == null) {
                continue;
            }
            imagesByProduct.computeIfAbsent(image.getProductId(), ignored -> new ArrayList<>())
                    .add(toProductImageDto(image, asset));
        }
        return imagesByProduct;
    }

    // ---- Reorder Product Images ----

    @Transactional
    public void reorderProductImages(UUID productId, List<ProductImageOrder> orders) {
        String tenantId = TenantContext.getCurrentTenantId();
        List<ProductImage> images = repository.findByProductIdOrderByPosition(productId, tenantId);

        Map<UUID, ProductImage> imageMap = new HashMap<>();
        for (ProductImage img : images) {
            imageMap.put(img.getId(), img);
        }

        List<ProductImage> updated = new ArrayList<>();
        for (ProductImageOrder order : orders) {
            ProductImage img = imageMap.get(order.imageId());
            if (img == null) {
                throw new MediaNotFoundException("ProductImage", order.imageId());
            }
            ProductImage reordered = new ProductImage(
                img.getId(), img.getTenantId(), img.getProductId(), img.getMediaAssetId(),
                order.position(), false, img.getCreatedAt()
            );
            updated.add(reordered);
        }

        // Set primary: the first in order or explicitly set
        if (!updated.isEmpty()) {
            // Find if any order explicitly sets primary
            boolean hasPrimary = orders.stream().anyMatch(ProductImageOrder::isPrimary);
            if (hasPrimary) {
                for (int i = 0; i < orders.size(); i++) {
                    if (orders.get(i).isPrimary() && i < updated.size()) {
                        ProductImage img = updated.get(i);
                        updated.set(i, new ProductImage(
                            img.getId(), img.getTenantId(), img.getProductId(), img.getMediaAssetId(),
                            img.getPosition(), true, img.getCreatedAt()
                        ));
                    }
                }
            } else {
                // First in order becomes primary
                ProductImage first = updated.get(0);
                updated.set(0, new ProductImage(
                    first.getId(), first.getTenantId(), first.getProductId(), first.getMediaAssetId(),
                    first.getPosition(), true, first.getCreatedAt()
                ));
            }
        }

        repository.updateProductImagePositions(updated);
    }

    // ---- Branding Asset ----

    /**
     * Replace the active branding asset atomically.
     * If the new asset is still PENDING, this method first marks the old branding
     * as PENDING_DELETE, then confirms the new asset in a single transaction so
     * PostgreSQL never sees two READY branding assets of the same type.
     * If the new asset is already READY, only the old branding is replaced.
     */
    @Transactional
    public MediaAssetDto setBrandingAsset(UUID assetId, AssetType assetType) {
        String tenantId = TenantContext.getCurrentTenantId();

        MediaAsset asset = repository.findById(assetId, tenantId)
            .orElseThrow(() -> new MediaNotFoundException("MediaAsset", assetId));

        if (asset.getAssetType() != assetType) {
            throw new MediaValidationException("MEDIA_ASSET_TYPE_MISMATCH",
                "El tipo del asset no coincide con el tipo de branding solicitado");
        }

        // Find and mark existing active branding as PENDING_DELETE first
        // This must happen BEFORE the new asset becomes READY to avoid
        // violating the unique branding constraint
        repository.findActiveBranding(tenantId, assetType).ifPresent(oldAsset -> {
            oldAsset.markPendingDelete(Instant.now(clock));
            repository.save(oldAsset);
        });

        // If the asset is still PENDING, confirm it now (within the same transaction)
        if (asset.getStatus() == AssetStatus.PENDING) {
            if (!objectStorage.exists(asset.getUploadKey())) {
                repository.failPendingAsset(assetId, tenantId, "Staging object not found");
                throw new MediaValidationException("MEDIA_STAGING_MISSING",
                    "El objeto de staging no existe para el asset de branding");
            }

            try (InputStream stream = objectStorage.download(asset.getUploadKey())) {
                ProcessingResult processingResult = imageProcessor.process(stream, asset.getDeclaredContentType());

                if (!processingResult.success()) {
                    objectStorage.delete(asset.getUploadKey());
                    repository.failPendingAsset(assetId, tenantId, processingResult.failureReason());
                    throw new MediaValidationException(processingResult.failureCode(),
                        processingResult.failureReason());
                }

                String finalObjectKey = generateFinalObjectKey(asset);
                objectStorage.upload(finalObjectKey, processingResult.optimizedBytes(), "image/webp");

                asset.markReady(finalObjectKey, processingResult.width(), processingResult.height(),
                    processingResult.sizeBytes(), Instant.now(clock));
                repository.save(asset);
                repository.commitQuotaUsage(tenantId, asset.getReservedBytes(), processingResult.sizeBytes());

                try {
                    objectStorage.delete(asset.getUploadKey());
                } catch (Exception ex) {
                    // Non-fatal: staging will expire
                }
            } catch (MediaValidationException e) {
                throw e;
            } catch (Exception e) {
                repository.failPendingAsset(assetId, tenantId, e.getMessage());
                throw new MediaValidationException("MEDIA_PROCESSING_ERROR", e.getMessage());
            }
        } else if (asset.getStatus() != AssetStatus.READY) {
            throw new MediaValidationException("MEDIA_ASSET_NOT_READY",
                "El asset debe estar en estado PENDING o READY para ser usado como branding");
        }

        String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
        return toDto(asset, cdnUrl);
    }

    @Transactional(readOnly = true)
    public MediaAssetDto getActiveBranding(AssetType assetType) {
        String tenantId = TenantContext.getCurrentTenantId();
        return repository.findActiveBranding(tenantId, assetType)
            .map(asset -> {
                String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
                return toDto(asset, cdnUrl);
            })
            .orElse(null);
    }

    // ---- Quota Stats ----

    @Transactional(readOnly = true)
    public TenantMediaQuota getQuotaStats() {
        String tenantId = TenantContext.getCurrentTenantId();
        return repository.getQuota(tenantId);
    }

    @Transactional
    public TenantMediaQuota updateQuotaLimits(long maxAssets, long maxStorageBytes) {
        if (maxAssets < 0 || maxStorageBytes < 0) {
            throw new MediaValidationException("MEDIA_INVALID_QUOTA_LIMITS",
                    "Los límites de cuota no pueden ser negativos");
        }
        String tenantId = TenantContext.getCurrentTenantId();
        TenantMediaQuota current = repository.getQuota(tenantId);
        if (maxAssets < current.effectiveUsedAssets()
                || maxStorageBytes < current.effectiveUsedBytes()) {
            throw new MediaValidationException("MEDIA_QUOTA_BELOW_CURRENT_USAGE",
                    "Los límites no pueden quedar por debajo del uso y las reservas actuales");
        }
        return repository.updateQuotaLimits(tenantId, maxAssets, maxStorageBytes);
    }

    // ---- Helpers ----

    private String generateFinalObjectKey(MediaAsset asset) {
        String ulid = UUID.randomUUID().toString().replace("-", "");
        if (asset.getAssetType() == AssetType.LOGO || asset.getAssetType() == AssetType.BANNER) {
            return "tenants/" + asset.getTenantId() + "/branding/"
                + asset.getAssetType().name().toLowerCase() + "/" + asset.getId() + "/" + ulid + ".webp";
        }
        return "tenants/" + asset.getTenantId() + "/assets/" + asset.getId() + "/" + ulid + ".webp";
    }

    private String extractExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    private MediaAssetDto toDto(MediaAsset asset, String cdnUrl) {
        return new MediaAssetDto(
            asset.getId(),
            cdnUrl,
            asset.getContentType(),
            asset.getSizeBytes(),
            asset.getWidth(),
            asset.getHeight(),
            asset.getStatus().name(),
            asset.getAssetType().name(),
            asset.getCreatedAt().toString()
        );
    }

    private ProductImageDto toProductImageDto(ProductImage pi, MediaAsset asset) {
        String cdnUrl = asset.getObjectKey() != null ? publicBaseUrl + "/" + asset.getObjectKey() : null;
        return new ProductImageDto(
            pi.getId(),
            pi.getMediaAssetId(),
            toDto(asset, cdnUrl),
            pi.getPosition(),
            pi.isPrimary()
        );
    }

    private String buildConfirmMessage(int ready, int failed, int total) {
        if (failed == 0) return null;
        if (ready == 0) return "Todas las " + total + " imágenes fallaron";
        return ready + " de " + total + " imágenes listas; " + failed + " no se pudo subir";
    }

    private String buildAssociateMessage(int associated, int rejected, int total) {
        if (rejected == 0) return null;
        if (associated == 0) return "Ninguna imagen se pudo asociar";
        return associated + " de " + total + " imágenes asociadas";
    }
}
