package com.rafaga.backend.media.infrastructure.web;

import com.rafaga.backend.media.application.dto.MediaAssetDto;
import com.rafaga.backend.media.application.dto.ProductImageDto;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontMediaControllerTest {

    @Mock private MediaService mediaService;

    private StorefrontMediaController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = new StorefrontMediaController(mediaService);
    }

    @Test
    void getProductImages_returns200() {
        UUID productId = UUID.randomUUID();
        when(mediaService.listProductImages(productId)).thenReturn(List.of());

        ResponseEntity<List<ProductImageDto>> result = controller.getProductImages(productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getProductImages_returnsProductImages() {
        UUID productId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        MediaAssetDto assetDto = new MediaAssetDto(
            assetId, "https://media.rafaga.com/key", "image/webp", 5000L, 800, 600, "READY", "PRODUCT", "2024-01-01T00:00:00Z"
        );
        ProductImageDto imageDto = new ProductImageDto(imageId, assetId, assetDto, 0, true);
        when(mediaService.listProductImages(productId)).thenReturn(List.of(imageDto));

        ResponseEntity<List<ProductImageDto>> result = controller.getProductImages(productId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).id()).isEqualTo(imageId);
        assertThat(result.getBody().get(0).position()).isEqualTo(0);
        assertThat(result.getBody().get(0).isPrimary()).isTrue();
        assertThat(result.getBody().get(0).asset().contentType()).isEqualTo("image/webp");
    }

    @Test
    void getBranding_returns200WithNulls() {
        when(mediaService.getActiveBranding(eq(AssetType.LOGO))).thenReturn(null);
        when(mediaService.getActiveBranding(eq(AssetType.BANNER))).thenReturn(null);

        ResponseEntity<StorefrontMediaController.BrandingResponse> result = controller.getBranding();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().logo()).isNull();
        assertThat(result.getBody().banner()).isNull();
    }

    @Test
    void getBranding_returnsLogoAndBanner() {
        UUID logoId = UUID.randomUUID();
        UUID bannerId = UUID.randomUUID();
        MediaAssetDto logoDto = new MediaAssetDto(
            logoId, "https://media.rafaga.com/logo", "image/webp", 5000L, 800, 200, "READY", "LOGO", "2024-01-01T00:00:00Z"
        );
        MediaAssetDto bannerDto = new MediaAssetDto(
            bannerId, "https://media.rafaga.com/banner", "image/webp", 10000L, 1920, 400, "READY", "BANNER", "2024-01-01T00:00:00Z"
        );
        when(mediaService.getActiveBranding(eq(AssetType.LOGO))).thenReturn(logoDto);
        when(mediaService.getActiveBranding(eq(AssetType.BANNER))).thenReturn(bannerDto);

        ResponseEntity<StorefrontMediaController.BrandingResponse> result = controller.getBranding();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().logo().id()).isEqualTo(logoId);
        assertThat(result.getBody().logo().assetType()).isEqualTo("LOGO");
        assertThat(result.getBody().banner().id()).isEqualTo(bannerId);
        assertThat(result.getBody().banner().assetType()).isEqualTo("BANNER");
    }

    @Test
    void getBranding_returnsOnlyLogo() {
        UUID logoId = UUID.randomUUID();
        MediaAssetDto logoDto = new MediaAssetDto(
            logoId, "https://media.rafaga.com/logo", "image/webp", 5000L, 800, 200, "READY", "LOGO", "2024-01-01T00:00:00Z"
        );
        when(mediaService.getActiveBranding(eq(AssetType.LOGO))).thenReturn(logoDto);
        when(mediaService.getActiveBranding(eq(AssetType.BANNER))).thenReturn(null);

        ResponseEntity<StorefrontMediaController.BrandingResponse> result = controller.getBranding();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().logo().id()).isEqualTo(logoId);
        assertThat(result.getBody().banner()).isNull();
    }
}
