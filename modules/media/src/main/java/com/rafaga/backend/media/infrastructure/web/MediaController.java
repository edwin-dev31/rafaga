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
import com.rafaga.backend.media.application.service.ProductImageOrder;
import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.AssetType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload/batch")
    public ResponseEntity<UploadBatch.Response> requestUploadBatch(
            @Valid @RequestBody @Size(min = 1, max = 8) List<UploadBatch.Request> items) {
        UploadBatch.Response response = mediaService.requestUploadBatch(items);
        int rejected = response.summary().rejected();
        int accepted = response.summary().accepted();
        if (accepted == 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/batch/confirm")
    public ResponseEntity<ConfirmBatch.Response> confirmUploadBatch(
            @Valid @RequestBody @Size(min = 1, max = 8) List<UUID> assetIds) {
        ConfirmBatch.Response response = mediaService.confirmUploadBatch(assetIds);
        if (response.summary().ready() == 0) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable("id") UUID id) {
        mediaService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel-deletion")
    public ResponseEntity<MediaAssetDto> cancelDeletion(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(mediaService.cancelDeletion(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaAssetDto> getAsset(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(mediaService.getAsset(id));
    }

    @GetMapping
    public ResponseEntity<List<MediaAssetDto>> listAssets(@RequestParam(name = "status", required = false) String status) {
        AssetStatus assetStatus = null;
        if (status != null) {
            try {
                assetStatus = AssetStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new MediaValidationException("MEDIA_INVALID_STATUS",
                    "Estado inválido: " + status + ". Valores: PENDING, READY, PENDING_DELETE, DELETED, FAILED");
            }
        }
        return ResponseEntity.ok(mediaService.listAssets(assetStatus));
    }

    @PostMapping("/products/{productId}/images")
    public ResponseEntity<ProductImageDto> associateToProduct(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AssociateBatch.Request item) {
        return ResponseEntity.ok(mediaService.associateToProduct(item.assetId(), productId, item.position(), item.isPrimary()));
    }

    @PostMapping("/products/{productId}/images/batch")
    public ResponseEntity<AssociateBatch.Response> associateToProductBatch(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody @Size(min = 1, max = 8) List<AssociateBatch.Request> items) {
        AssociateBatch.Response response = mediaService.associateToProductBatch(productId, items);
        if (response.summary().associated() == 0) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<Void> dissociateFromProduct(
            @PathVariable("productId") UUID productId,
            @PathVariable("imageId") UUID imageId) {
        mediaService.dissociateFromProduct(imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/{productId}/images")
    public ResponseEntity<List<ProductImageDto>> listProductImages(@PathVariable("productId") UUID productId) {
        return ResponseEntity.ok(mediaService.listProductImages(productId));
    }

    @PutMapping("/products/{productId}/images/order")
    public ResponseEntity<Void> reorderProductImages(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody @Size(min = 1, max = 8) List<ProductImageOrder> orders) {
        // Validate positions are non-negative and unique
        if (orders.stream().anyMatch(o -> o.position() < 0)) {
            throw new MediaValidationException("MEDIA_INVALID_POSITION",
                "Las posiciones deben ser no negativas");
        }
        mediaService.reorderProductImages(productId, orders);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/branding/{assetType}/upload")
    public ResponseEntity<MediaAssetDto> setBrandingAsset(
            @PathVariable("assetType") String assetTypeStr,
            @Valid @RequestBody BrandingUploadRequest request) {
        AssetType assetType;
        try {
            assetType = AssetType.valueOf(assetTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new MediaValidationException("MEDIA_INVALID_ASSET_TYPE",
                "Tipo de asset inválido: " + assetTypeStr + ". Valores: PRODUCT, LOGO, BANNER");
        }
        MediaAssetDto dto = mediaService.setBrandingAsset(request.assetId(), assetType);
        return ResponseEntity.ok(dto);
    }

    record BrandingUploadRequest(@NotNull(message = "assetId is required") UUID assetId) {}

    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<com.rafaga.backend.common.application.ApiError> handleValidation(MediaValidationException e) {
        return ResponseEntity.badRequest()
                .body(com.rafaga.backend.common.application.ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<com.rafaga.backend.common.application.ApiError> handleNotFound(MediaNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(com.rafaga.backend.common.application.ApiError.of("MEDIA_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MediaConflictException.class)
    public ResponseEntity<com.rafaga.backend.common.application.ApiError> handleConflict(MediaConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(com.rafaga.backend.common.application.ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<com.rafaga.backend.common.application.ApiError> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .findFirst()
            .orElse(e.getMessage());
        return ResponseEntity.badRequest()
                .body(com.rafaga.backend.common.application.ApiError.of("MEDIA_VALIDATION_ERROR", message));
    }
}
