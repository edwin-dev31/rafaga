package com.rafaga.backend.media.infrastructure.web;

import com.rafaga.backend.media.application.dto.MediaAssetDto;
import com.rafaga.backend.media.application.dto.ProductImageDto;
import com.rafaga.backend.media.application.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront")
public class StorefrontMediaController {

    private final MediaService mediaService;

    public StorefrontMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/products/{productId}/images")
    public ResponseEntity<List<ProductImageDto>> getProductImages(@PathVariable("productId") UUID productId) {
        return ResponseEntity.ok(mediaService.listProductImages(productId));
    }

    @GetMapping("/branding")
    public ResponseEntity<BrandingResponse> getBranding() {
        MediaAssetDto logo = mediaService.getActiveBranding(com.rafaga.backend.media.domain.model.AssetType.LOGO);
        MediaAssetDto banner = mediaService.getActiveBranding(com.rafaga.backend.media.domain.model.AssetType.BANNER);
        return ResponseEntity.ok(new BrandingResponse(logo, banner));
    }

    record BrandingResponse(MediaAssetDto logo, MediaAssetDto banner) {}
}
