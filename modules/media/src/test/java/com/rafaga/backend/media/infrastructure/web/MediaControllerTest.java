package com.rafaga.backend.media.infrastructure.web;

import com.rafaga.backend.media.application.dto.AssociateBatch;
import com.rafaga.backend.media.application.dto.ConfirmBatch;
import com.rafaga.backend.media.application.dto.MediaAssetDto;
import com.rafaga.backend.media.application.dto.ProductImageDto;
import com.rafaga.backend.media.application.dto.UploadBatch;
import com.rafaga.backend.media.application.exception.MediaConflictException;
import com.rafaga.backend.media.application.exception.MediaNotFoundException;
import com.rafaga.backend.media.application.exception.MediaValidationException;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock private MediaService mediaService;

    private MediaController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = new MediaController(mediaService);
    }

    @Test
    void requestUploadBatch_returns200() {
        UploadBatch.Response response = new UploadBatch.Response(
            List.of(new UploadBatch.ItemResponse("PENDING", UUID.randomUUID(), "key", "url", Instant.now(), null)),
            new UploadBatch.Summary(1, 1, 0)
        );
        when(mediaService.requestUploadBatch(anyList())).thenReturn(response);

        ResponseEntity<UploadBatch.Response> result = controller.requestUploadBatch(
            List.of(new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000))
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().summary().accepted()).isEqualTo(1);
    }

    @Test
    void requestUploadBatch_returns429WhenAllRejected() {
        UploadBatch.Response response = new UploadBatch.Response(
            List.of(new UploadBatch.ItemResponse("REJECTED", null, null, null, null,
                new UploadBatch.Error("MEDIA_QUOTA_EXCEEDED", "Cuota excedida"))),
            new UploadBatch.Summary(1, 0, 1)
        );
        when(mediaService.requestUploadBatch(anyList())).thenReturn(response);

        ResponseEntity<UploadBatch.Response> result = controller.requestUploadBatch(
            List.of(new UploadBatch.Request(AssetType.PRODUCT, "image/jpeg", 1000))
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void confirmUploadBatch_returns200() {
        UUID assetId = UUID.randomUUID();
        ConfirmBatch.Response response = new ConfirmBatch.Response(
            null,
            List.of(new ConfirmBatch.ItemResponse(assetId, "READY",
                new MediaAssetDto(assetId, "url", "image/webp", 5000L, 800, 600, "READY", "PRODUCT", "2024-01-01T00:00:00Z"), null)),
            new ConfirmBatch.Summary(1, 1, 0)
        );
        when(mediaService.confirmUploadBatch(anyList())).thenReturn(response);

        ResponseEntity<ConfirmBatch.Response> result = controller.confirmUploadBatch(List.of(assetId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().summary().ready()).isEqualTo(1);
    }

    @Test
    void confirmUploadBatch_returns422WhenAllFailed() {
        UUID assetId = UUID.randomUUID();
        ConfirmBatch.Response response = new ConfirmBatch.Response(
            "Todas las 1 imágenes fallaron",
            List.of(new ConfirmBatch.ItemResponse(assetId, "FAILED", null,
                new ConfirmBatch.Error("MEDIA_STAGING_MISSING", "No existe", null, null))),
            new ConfirmBatch.Summary(1, 0, 1)
        );
        when(mediaService.confirmUploadBatch(anyList())).thenReturn(response);

        ResponseEntity<ConfirmBatch.Response> result = controller.confirmUploadBatch(List.of(assetId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void deleteAsset_returns204() {
        UUID assetId = UUID.randomUUID();
        ResponseEntity<Void> result = controller.deleteAsset(assetId);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteAsset_returns409WhenInUse() {
        MediaController.ErrorResponse response = controller.handleConflict(
            new MediaConflictException("MEDIA_ASSET_IN_USE", "En uso")
        ).getBody();
        assertThat(response.code()).isEqualTo("MEDIA_ASSET_IN_USE");
        assertThat(response.message()).isEqualTo("En uso");
    }

    @Test
    void deleteAsset_returns404WhenNotFound() {
        UUID assetId = UUID.randomUUID();
        MediaController.ErrorResponse response = controller.handleNotFound(
            new MediaNotFoundException("MediaAsset", assetId)
        ).getBody();
        assertThat(response.code()).isEqualTo("MEDIA_NOT_FOUND");
    }

    @Test
    void cancelDeletion_returns200() {
        UUID assetId = UUID.randomUUID();
        MediaAssetDto dto = new MediaAssetDto(assetId, "url", "image/webp", 5000L, 800, 600, "READY", "PRODUCT", "2024-01-01T00:00:00Z");
        when(mediaService.cancelDeletion(assetId)).thenReturn(dto);

        ResponseEntity<MediaAssetDto> result = controller.cancelDeletion(assetId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo("READY");
    }

    @Test
    void getAsset_returns200() {
        UUID assetId = UUID.randomUUID();
        MediaAssetDto dto = new MediaAssetDto(assetId, "url", "image/webp", 5000L, 800, 600, "READY", "PRODUCT", "2024-01-01T00:00:00Z");
        when(mediaService.getAsset(assetId)).thenReturn(dto);

        ResponseEntity<MediaAssetDto> result = controller.getAsset(assetId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().id()).isEqualTo(assetId);
    }

    @Test
    void listAssets_returnsAll() {
        when(mediaService.listAssets(any())).thenReturn(List.of());

        ResponseEntity<List<MediaAssetDto>> result = controller.listAssets(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listAssets_filtersByStatus() {
        when(mediaService.listAssets(any())).thenReturn(List.of());

        ResponseEntity<List<MediaAssetDto>> result = controller.listAssets("READY");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listAssets_returns400ForInvalidStatus() {
        MediaController.ErrorResponse response = controller.handleValidation(
            new MediaValidationException("MEDIA_INVALID_STATUS", "Estado inválido: INVALID")
        ).getBody();
        assertThat(response.code()).isEqualTo("MEDIA_INVALID_STATUS");
    }

    @Test
    void associateToProduct_returns200() {
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAssetDto assetDto = new MediaAssetDto(assetId, "url", "image/webp", 5000L, 800, 600, "READY", "PRODUCT", "2024-01-01T00:00:00Z");
        ProductImageDto dto = new ProductImageDto(UUID.randomUUID(), assetId, assetDto, 0, true);
        when(mediaService.associateToProduct(eq(assetId), eq(productId), eq(0), eq(true))).thenReturn(dto);

        ResponseEntity<ProductImageDto> result = controller.associateToProduct(
            productId, new AssociateBatch.Request(assetId, 0, true)
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().position()).isEqualTo(0);
    }

    @Test
    void associateToProductBatch_returns200() {
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        AssociateBatch.Response response = new AssociateBatch.Response(
            null,
            List.of(new AssociateBatch.ItemResponse(assetId, "ASSOCIATED", UUID.randomUUID(), null)),
            new AssociateBatch.Summary(1, 1, 0)
        );
        when(mediaService.associateToProductBatch(eq(productId), anyList())).thenReturn(response);

        ResponseEntity<AssociateBatch.Response> result = controller.associateToProductBatch(
            productId, List.of(new AssociateBatch.Request(assetId, 0, false))
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().summary().associated()).isEqualTo(1);
    }

    @Test
    void dissociateFromProduct_returns204() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        ResponseEntity<Void> result = controller.dissociateFromProduct(productId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void listProductImages_returns200() {
        UUID productId = UUID.randomUUID();
        when(mediaService.listProductImages(productId)).thenReturn(List.of());

        ResponseEntity<List<ProductImageDto>> result = controller.listProductImages(productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void setBrandingAsset_returns200() {
        UUID assetId = UUID.randomUUID();
        MediaAssetDto dto = new MediaAssetDto(assetId, "url", "image/webp", 5000L, 800, 600, "READY", "LOGO", "2024-01-01T00:00:00Z");
        when(mediaService.setBrandingAsset(eq(assetId), eq(AssetType.LOGO))).thenReturn(dto);

        ResponseEntity<MediaAssetDto> result = controller.setBrandingAsset(
            "LOGO", new MediaController.BrandingUploadRequest(assetId)
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().assetType()).isEqualTo("LOGO");
    }

    @Test
    void validationError_returns400() {
        ResponseEntity<MediaController.ErrorResponse> result = controller.handleValidation(
            new MediaValidationException("CODE", "message")
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().code()).isEqualTo("CODE");
    }
}
