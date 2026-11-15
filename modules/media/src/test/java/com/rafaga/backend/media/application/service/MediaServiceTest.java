package com.rafaga.backend.media.application.service;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.dto.AssociateBatch;
import com.rafaga.backend.media.application.dto.ConfirmBatch;
import com.rafaga.backend.media.application.dto.MediaAssetDto;
import com.rafaga.backend.media.application.dto.MediaAdminSummary;
import com.rafaga.backend.media.application.dto.ProductImageDto;
import com.rafaga.backend.media.application.dto.UploadBatch;
import com.rafaga.backend.media.application.exception.MediaConflictException;
import com.rafaga.backend.media.application.exception.MediaNotFoundException;
import com.rafaga.backend.media.application.exception.MediaValidationException;
import com.rafaga.backend.media.application.port.out.CatalogPort;
import com.rafaga.backend.media.application.port.out.MediaRepository;
import com.rafaga.backend.media.application.port.out.ObjectStorage;
import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.AssetType;
import com.rafaga.backend.media.domain.model.MediaAsset;
import com.rafaga.backend.media.domain.model.ProductImage;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import com.rafaga.backend.media.domain.service.ImageProcessor;
import com.rafaga.backend.media.domain.service.ProcessingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    @Mock private MediaRepository repository;
    @Mock private ObjectStorage objectStorage;
    @Mock private ImageProcessor imageProcessor;
    @Mock private CatalogPort catalogPort;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(repository, objectStorage, imageProcessor, catalogPort, CLOCK);
        ReflectionTestUtils.setField(mediaService, "presignExpiryMinutes", 5);
        ReflectionTestUtils.setField(mediaService, "maxOutputSizeBytes", 5_242_880L);
        ReflectionTestUtils.setField(mediaService, "maxInputSizeBytes", 10_485_760L);
        ReflectionTestUtils.setField(mediaService, "publicBaseUrl", "https://media.rafaga.com");
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- requestUploadBatch ----

    @Test
    void requestUploadBatch_createsPresignedUrls() {
        when(repository.reserveQuota(TENANT_ID, 5_242_880)).thenReturn(true);
        when(objectStorage.createUploadUrl(anyString(), eq("image/jpeg"), any(Duration.class)))
            .thenReturn(new ObjectStorage.PresignedUpload("http://r2/upload", "key", NOW.plus(Duration.ofMinutes(5))));

        List<UploadBatch.Request> items = List.of(
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000)
        );

        UploadBatch.Response response = mediaService.requestUploadBatch(items);

        assertThat(response.summary().accepted()).isEqualTo(1);
        assertThat(response.summary().rejected()).isEqualTo(0);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo("PENDING");
        assertThat(response.items().get(0).assetId()).isNotNull();
        assertThat(response.items().get(0).uploadUrl()).isEqualTo("http://r2/upload");
        verify(repository).save(any(MediaAsset.class));
    }

    @Test
    void requestUploadBatch_rejectsWhenQuotaExceeded() {
        when(repository.reserveQuota(TENANT_ID, 5_242_880)).thenReturn(false);

        List<UploadBatch.Request> items = List.of(
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000)
        );

        UploadBatch.Response response = mediaService.requestUploadBatch(items);

        assertThat(response.summary().accepted()).isEqualTo(0);
        assertThat(response.summary().rejected()).isEqualTo(1);
        assertThat(response.items().get(0).status()).isEqualTo("REJECTED");
        assertThat(response.items().get(0).error().code()).isEqualTo("MEDIA_QUOTA_EXCEEDED");
    }

    @Test
    void requestUploadBatch_rejectsEmptyBatch() {
        assertThatThrownBy(() -> mediaService.requestUploadBatch(List.of()))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_BATCH_SIZE_INVALID");
    }

    @Test
    void requestUploadBatch_rejectsBatchLargerThan8() {
        List<UploadBatch.Request> items = List.of(
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000),
            new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000)
        );

        assertThatThrownBy(() -> mediaService.requestUploadBatch(items))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_BATCH_SIZE_INVALID");
    }

    // ---- confirmUploadBatch ----

    @Test
    void confirmUploadBatch_processesAndConfirmsAsset() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(objectStorage.exists("upload/key")).thenReturn(true);
        when(objectStorage.getMetadata("upload/key"))
            .thenReturn(new ObjectStorage.Metadata("image/jpeg", 10000L));
        when(objectStorage.download("upload/key"))
            .thenReturn(new ByteArrayInputStream(new byte[100]));
        when(imageProcessor.process(any(InputStream.class), eq("image/jpeg")))
            .thenReturn(new ProcessingResult(new byte[5000], "image/webp", 800, 600, 5000, true, null, null));

        List<UUID> assetIds = List.of(assetId);
        ConfirmBatch.Response response = mediaService.confirmUploadBatch(assetIds);

        assertThat(response.summary().ready()).isEqualTo(1);
        assertThat(response.summary().failed()).isEqualTo(0);
        assertThat(response.results().get(0).status()).isEqualTo("READY");
        assertThat(response.results().get(0).asset().status()).isEqualTo("READY");
        assertThat(response.results().get(0).asset().contentType()).isEqualTo("image/webp");
        verify(objectStorage).delete("upload/key");
        verify(repository).commitQuotaUsage(eq(TENANT_ID), eq(5_242_880L), eq(5000L));
    }

    @Test
    void confirmUploadBatch_failsWhenStagingMissing() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(objectStorage.exists("upload/key")).thenReturn(false);

        ConfirmBatch.Response response = mediaService.confirmUploadBatch(List.of(assetId));

        assertThat(response.summary().failed()).isEqualTo(1);
        assertThat(response.results().get(0).error().code()).isEqualTo("MEDIA_STAGING_MISSING");
        verify(repository).releaseQuotaReservation(TENANT_ID, 5_242_880L);
    }

    @Test
    void confirmUploadBatch_failsWhenInputTooLarge() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(objectStorage.exists("upload/key")).thenReturn(true);
        when(objectStorage.getMetadata("upload/key"))
            .thenReturn(new ObjectStorage.Metadata("image/jpeg", 20_000_000L));

        ConfirmBatch.Response response = mediaService.confirmUploadBatch(List.of(assetId));

        assertThat(response.summary().failed()).isEqualTo(1);
        assertThat(response.results().get(0).error().code()).isEqualTo("MEDIA_INPUT_TOO_LARGE");
        verify(repository).releaseQuotaReservation(TENANT_ID, 5_242_880L);
        verify(objectStorage).delete("upload/key");
    }

    @Test
    void confirmUploadBatch_isIdempotentForNonPendingAssets() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        ConfirmBatch.Response response = mediaService.confirmUploadBatch(List.of(assetId));

        assertThat(response.summary().failed()).isEqualTo(1);
        assertThat(response.results().get(0).error().code()).isEqualTo("MEDIA_NOT_PENDING");
        verify(objectStorage, never()).download(anyString());
    }

    @Test
    void confirmUploadBatch_deduplicatesAssetIds() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(objectStorage.exists("upload/key")).thenReturn(true);
        when(objectStorage.getMetadata("upload/key"))
            .thenReturn(new ObjectStorage.Metadata("image/jpeg", 10000L));
        when(objectStorage.download("upload/key"))
            .thenReturn(new ByteArrayInputStream(new byte[100]));
        when(imageProcessor.process(any(InputStream.class), eq("image/jpeg")))
            .thenReturn(new ProcessingResult(new byte[5000], "image/webp", 800, 600, 5000, true, null, null));

        // Pass the same assetId twice
        ConfirmBatch.Response response = mediaService.confirmUploadBatch(List.of(assetId, assetId));

        assertThat(response.summary().ready()).isEqualTo(1);
        assertThat(response.results()).hasSize(1);
        verify(repository, times(1)).findById(assetId, TENANT_ID);
    }

    @Test
    void confirmUploadBatch_rejectsEmptyBatch() {
        assertThatThrownBy(() -> mediaService.confirmUploadBatch(List.of()))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_BATCH_SIZE_INVALID");
    }

    // ---- deleteAsset ----

    @Test
    void deleteAsset_marksPendingDelete() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(repository.existsByMediaAssetId(assetId, TENANT_ID)).thenReturn(false);

        mediaService.deleteAsset(assetId);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PENDING_DELETE);
        verify(repository).save(asset);
    }

    @Test
    void deleteAsset_throwsWhenAssetAssociatedToProduct() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(repository.existsByMediaAssetId(assetId, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> mediaService.deleteAsset(assetId))
            .isInstanceOf(MediaConflictException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_ASSET_IN_USE");
    }

    @Test
    void deleteAsset_throwsWhenNotFound() {
        when(repository.findById(any(UUID.class), eq(TENANT_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.deleteAsset(UUID.randomUUID()))
            .isInstanceOf(MediaNotFoundException.class);
    }

    // ---- cancelDeletion ----

    @Test
    void cancelDeletion_restoresReadyStatus() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);
        asset.markPendingDelete(NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        MediaAssetDto dto = mediaService.cancelDeletion(assetId);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(dto.status()).isEqualTo("READY");
    }

    // ---- getAsset ----

    @Test
    void getAsset_returnsDto() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        MediaAssetDto dto = mediaService.getAsset(assetId);

        assertThat(dto.id()).isEqualTo(assetId);
        assertThat(dto.status()).isEqualTo("READY");
        assertThat(dto.width()).isEqualTo(800);
        assertThat(dto.height()).isEqualTo(600);
    }

    // ---- listAssets ----

    @Test
    void listAssets_returnsAllWhenNoStatus() {
        when(repository.findByTenantAndStatus(eq(TENANT_ID), any(AssetStatus.class))).thenReturn(List.of());

        List<MediaAssetDto> assets = mediaService.listAssets(null);

        assertThat(assets).isEmpty();
        verify(repository, times(5)).findByTenantAndStatus(eq(TENANT_ID), any(AssetStatus.class));
    }

    @Test
    void listAssets_filtersByStatus() {
        when(repository.findByTenantAndStatus(TENANT_ID, AssetStatus.READY)).thenReturn(List.of());

        List<MediaAssetDto> assets = mediaService.listAssets(AssetStatus.READY);

        assertThat(assets).isEmpty();
        verify(repository).findByTenantAndStatus(TENANT_ID, AssetStatus.READY);
    }

    // ---- associateToProduct ----

    @Test
    void associateToProduct_createsProductImage() {
        UUID assetId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(catalogPort.productExists(productId, TENANT_ID)).thenReturn(true);

        ProductImageDto dto = mediaService.associateToProduct(assetId, productId, 0, true);

        assertThat(dto.mediaAssetId()).isEqualTo(assetId);
        assertThat(dto.position()).isEqualTo(0);
        assertThat(dto.isPrimary()).isTrue();
        verify(repository).saveProductImage(any(ProductImage.class));
    }

    @Test
    void associateToProduct_throwsWhenAssetNotReady() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> mediaService.associateToProduct(assetId, UUID.randomUUID(), 0, false))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_ASSET_NOT_READY");
    }

    @Test
    void associateToProduct_throwsWhenProductNotFound() {
        UUID assetId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(catalogPort.productExists(productId, TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> mediaService.associateToProduct(assetId, productId, 0, false))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "PRODUCT_NOT_FOUND");
    }

    // ---- associateToProductBatch ----

    @Test
    void associateToProductBatch_associatesMultipleAssets() {
        UUID productId = UUID.randomUUID();
        UUID assetId1 = UUID.randomUUID();
        UUID assetId2 = UUID.randomUUID();
        MediaAsset asset1 = new MediaAsset(assetId1, TENANT_ID, "upload/key1", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        MediaAsset asset2 = new MediaAsset(assetId2, TENANT_ID, "upload/key2", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset1.markReady("final/key1", 800, 600, 5000, NOW);
        asset2.markReady("final/key2", 800, 600, 5000, NOW);

        when(repository.findById(assetId1, TENANT_ID)).thenReturn(Optional.of(asset1));
        when(repository.findById(assetId2, TENANT_ID)).thenReturn(Optional.of(asset2));
        when(catalogPort.productExists(productId, TENANT_ID)).thenReturn(true);

        List<AssociateBatch.Request> items = List.of(
            new AssociateBatch.Request(assetId1, 0, true),
            new AssociateBatch.Request(assetId2, 1, false)
        );

        AssociateBatch.Response response = mediaService.associateToProductBatch(productId, items);

        assertThat(response.summary().associated()).isEqualTo(2);
        assertThat(response.summary().rejected()).isEqualTo(0);
    }

    @Test
    void associateToProductBatch_rejectsMultiplePrimary() {
        List<AssociateBatch.Request> items = List.of(
            new AssociateBatch.Request(UUID.randomUUID(), 0, true),
            new AssociateBatch.Request(UUID.randomUUID(), 1, true)
        );

        assertThatThrownBy(() -> mediaService.associateToProductBatch(UUID.randomUUID(), items))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_MULTIPLE_PRIMARY");
    }

    // ---- dissociateFromProduct ----

    @Test
    void dissociateFromProduct_deletesProductImage() {
        UUID imageId = UUID.randomUUID();

        mediaService.dissociateFromProduct(imageId);

        verify(repository).deleteProductImage(imageId, TENANT_ID);
    }

    // ---- reorderProductImages ----

    @Test
    void reorderProductImages_updatesPositions() {
        UUID productId = UUID.randomUUID();
        UUID imageId1 = UUID.randomUUID();
        UUID imageId2 = UUID.randomUUID();
        ProductImage image1 = new ProductImage(imageId1, TENANT_ID, productId, UUID.randomUUID(), 0, true, NOW);
        ProductImage image2 = new ProductImage(imageId2, TENANT_ID, productId, UUID.randomUUID(), 1, false, NOW);

        when(repository.findByProductIdOrderByPosition(productId, TENANT_ID))
            .thenReturn(List.of(image1, image2));

        List<ProductImageOrder> orders = List.of(
            new ProductImageOrder(imageId2, 0, false),
            new ProductImageOrder(imageId1, 1, false)
        );

        mediaService.reorderProductImages(productId, orders);

        verify(repository).updateProductImagePositions(anyList());
    }

    // ---- setBrandingAsset ----

    @Test
    void setBrandingAsset_confirmsPendingAsset() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.LOGO, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(repository.findActiveBranding(TENANT_ID, AssetType.LOGO)).thenReturn(Optional.empty());
        when(objectStorage.exists("upload/key")).thenReturn(true);
        when(objectStorage.download("upload/key"))
            .thenReturn(new ByteArrayInputStream(new byte[100]));
        when(imageProcessor.process(any(InputStream.class), eq("image/jpeg")))
            .thenReturn(new ProcessingResult(new byte[5000], "image/webp", 800, 600, 5000, true, null, null));

        MediaAssetDto dto = mediaService.setBrandingAsset(assetId, AssetType.LOGO);

        assertThat(dto.status()).isEqualTo("READY");
        assertThat(dto.assetType()).isEqualTo("LOGO");
    }

    @Test
    void setBrandingAsset_replacesExistingBranding() {
        UUID newAssetId = UUID.randomUUID();
        UUID oldAssetId = UUID.randomUUID();
        MediaAsset newAsset = new MediaAsset(newAssetId, TENANT_ID, "upload/new", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.LOGO, NOW);
        MediaAsset oldAsset = new MediaAsset(oldAssetId, TENANT_ID, "upload/old", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.LOGO, NOW);
        oldAsset.markReady("final/old", 800, 600, 5000, NOW);

        when(repository.findById(newAssetId, TENANT_ID)).thenReturn(Optional.of(newAsset));
        when(repository.findActiveBranding(TENANT_ID, AssetType.LOGO)).thenReturn(Optional.of(oldAsset));
        when(objectStorage.exists("upload/new")).thenReturn(true);
        when(objectStorage.download("upload/new"))
            .thenReturn(new ByteArrayInputStream(new byte[100]));
        when(imageProcessor.process(any(InputStream.class), eq("image/jpeg")))
            .thenReturn(new ProcessingResult(new byte[5000], "image/webp", 800, 600, 5000, true, null, null));

        mediaService.setBrandingAsset(newAssetId, AssetType.LOGO);

        assertThat(oldAsset.getStatus()).isEqualTo(AssetStatus.PENDING_DELETE);
        verify(repository).save(oldAsset);
    }

    @Test
    void setBrandingAsset_throwsWhenAssetTypeMismatch() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);

        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> mediaService.setBrandingAsset(assetId, AssetType.LOGO))
            .isInstanceOf(MediaValidationException.class)
            .hasFieldOrPropertyWithValue("code", "MEDIA_ASSET_TYPE_MISMATCH");
    }

    // ---- getActiveBranding ----

    @Test
    void getActiveBranding_returnsNullWhenNone() {
        when(repository.findActiveBranding(TENANT_ID, AssetType.LOGO)).thenReturn(Optional.empty());

        MediaAssetDto dto = mediaService.getActiveBranding(AssetType.LOGO);

        assertThat(dto).isNull();
    }

    @Test
    void getActiveBranding_returnsDtoWhenExists() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.LOGO, NOW);
        asset.markReady("final/logo", 800, 600, 5000, NOW);

        when(repository.findActiveBranding(TENANT_ID, AssetType.LOGO)).thenReturn(Optional.of(asset));

        MediaAssetDto dto = mediaService.getActiveBranding(AssetType.LOGO);

        assertThat(dto).isNotNull();
        assertThat(dto.assetType()).isEqualTo("LOGO");
    }

    // ---- getQuotaStats ----

    @Test
    void getQuotaStats_returnsQuota() {
        TenantMediaQuota quota = new TenantMediaQuota(TENANT_ID, 500, 5_368_709_120L, 10, 5_000_000L, 2, 1_000_000L);
        when(repository.getQuota(TENANT_ID)).thenReturn(quota);

        TenantMediaQuota result = mediaService.getQuotaStats();

        assertThat(result).isEqualTo(quota);
        assertThat(result.effectiveUsedAssets()).isEqualTo(12);
        assertThat(result.effectiveUsedBytes()).isEqualTo(6_000_000L);
    }

    @Test
    void getAdminSummary_countsEveryStatusAndOnlyExposesRecentFailuresAndPendingCleanup() {
        MediaAsset failed = MediaAsset.fromDb(UUID.randomUUID(), TENANT_ID, "staging/failed", NOW,
                0, "image/jpeg", AssetType.PRODUCT, NOW, null, null, null, null, null,
                AssetStatus.FAILED, "MEDIA_PROCESSING_FAILED", "invalid image", NOW.plusSeconds(10), null, null);
        MediaAsset pendingDelete = MediaAsset.fromDb(UUID.randomUUID(), TENANT_ID, null, null,
                0, "image/jpeg", AssetType.PRODUCT, NOW, "final/object.webp", "image/webp", 100L, 10, 10,
                AssetStatus.PENDING_DELETE, null, null, NOW.plusSeconds(5), NOW.plusSeconds(4), null);
        TenantMediaQuota quota = new TenantMediaQuota(TENANT_ID, 100, 20_000_000L,
                2, 100L, 1, 50L);
        when(repository.findByTenantAndStatus(TENANT_ID, AssetStatus.FAILED)).thenReturn(List.of(failed));
        when(repository.findByTenantAndStatus(TENANT_ID, AssetStatus.PENDING_DELETE)).thenReturn(List.of(pendingDelete));
        when(repository.findByTenantAndStatus(TENANT_ID, AssetStatus.PENDING)).thenReturn(List.of());
        when(repository.findByTenantAndStatus(TENANT_ID, AssetStatus.READY)).thenReturn(List.of());
        when(repository.findByTenantAndStatus(TENANT_ID, AssetStatus.DELETED)).thenReturn(List.of());
        when(repository.getQuota(TENANT_ID)).thenReturn(quota);

        MediaAdminSummary summary = mediaService.getAdminSummary();

        assertThat(summary.assetsByStatus()).containsEntry(AssetStatus.FAILED, 1L)
                .containsEntry(AssetStatus.PENDING_DELETE, 1L)
                .containsEntry(AssetStatus.READY, 0L);
        assertThat(summary.recentFailures()).singleElement()
                .extracting(MediaAdminSummary.FailedAsset::failureCode)
                .isEqualTo("MEDIA_PROCESSING_FAILED");
        assertThat(summary.pendingCleanupAssets()).singleElement()
                .extracting(MediaAdminSummary.PendingCleanupAsset::id)
                .isEqualTo(pendingDelete.getId());
        assertThat(summary.quota()).isEqualTo(quota);
    }

    @Test
    void updateQuotaLimits_rejectsLimitsBelowCurrentUsage() {
        TenantMediaQuota current = new TenantMediaQuota(TENANT_ID, 500, 10_000_000L,
                10, 5_000_000L, 2, 1_000_000L);
        when(repository.getQuota(TENANT_ID)).thenReturn(current);

        assertThatThrownBy(() -> mediaService.updateQuotaLimits(11, 5_999_999L))
                .isInstanceOf(MediaValidationException.class)
                .hasMessageContaining("uso y las reservas actuales");
    }

    @Test
    void updateQuotaLimits_persistsValidLimits() {
        TenantMediaQuota current = new TenantMediaQuota(TENANT_ID, 500, 10_000_000L,
                10, 5_000_000L, 2, 1_000_000L);
        TenantMediaQuota updated = new TenantMediaQuota(TENANT_ID, 100, 20_000_000L,
                10, 5_000_000L, 2, 1_000_000L);
        when(repository.getQuota(TENANT_ID)).thenReturn(current);
        when(repository.updateQuotaLimits(TENANT_ID, 100, 20_000_000L)).thenReturn(updated);

        TenantMediaQuota result = mediaService.updateQuotaLimits(100, 20_000_000L);

        assertThat(result).isEqualTo(updated);
    }

    // ---- listProductImages ----

    @Test
    void listProductImages_returnsOrderedImages() {
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady("final/key", 800, 600, 5000, NOW);
        ProductImage image = new ProductImage(imageId, TENANT_ID, productId, assetId, 0, true, NOW);

        when(repository.findByProductIdOrderByPosition(productId, TENANT_ID)).thenReturn(List.of(image));
        when(repository.findById(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        List<ProductImageDto> result = mediaService.listProductImages(productId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).position()).isEqualTo(0);
        assertThat(result.get(0).isPrimary()).isTrue();
    }

    @Test
    void listProductImagesByProductIds_returnsAllImagesGroupedByProduct() {
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        UUID firstAssetId = UUID.randomUUID();
        UUID secondAssetId = UUID.randomUUID();
        MediaAsset firstAsset = readyAsset(firstAssetId, "first/final.webp", 800, 600);
        MediaAsset secondAsset = readyAsset(secondAssetId, "second/final.webp", 640, 480);
        ProductImage firstImage = new ProductImage(UUID.randomUUID(), TENANT_ID, firstProductId,
                firstAssetId, 0, true, NOW);
        ProductImage secondImage = new ProductImage(UUID.randomUUID(), TENANT_ID, secondProductId,
                secondAssetId, 0, true, NOW);

        when(repository.findByProductIdsOrderByProductIdAndPosition(
                List.of(firstProductId, secondProductId), TENANT_ID))
                .thenReturn(List.of(firstImage, secondImage));
        when(repository.findByIds(any(), eq(TENANT_ID))).thenReturn(Map.of(
                firstAssetId, firstAsset,
                secondAssetId, secondAsset));

        Map<UUID, List<ProductImageDto>> result = mediaService.listProductImagesByProductIds(
                List.of(firstProductId, secondProductId));

        assertThat(result).containsKeys(firstProductId, secondProductId);
        assertThat(result.get(firstProductId)).singleElement()
                .extracting(ProductImageDto::mediaAssetId)
                .isEqualTo(firstAssetId);
        assertThat(result.get(secondProductId)).singleElement()
                .extracting(ProductImageDto::mediaAssetId)
                .isEqualTo(secondAssetId);
    }

    private MediaAsset readyAsset(UUID assetId, String objectKey, int width, int height) {
        MediaAsset asset = new MediaAsset(assetId, TENANT_ID, "upload/key", NOW.plus(Duration.ofMinutes(5)),
                5_242_880, "image/jpeg", AssetType.PRODUCT, NOW);
        asset.markReady(objectKey, width, height, 5000, NOW);
        return asset;
    }
}
