package com.rafaga.backend.media.application;

import com.rafaga.backend.catalog.application.port.StorefrontProductImageLookup;
import com.rafaga.backend.media.application.dto.ProductImageDto;
import com.rafaga.backend.media.application.service.MediaService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Bridges the media public API into the catalog-owned storefront contract. */
@Service
class MediaStorefrontProductImageLookup implements StorefrontProductImageLookup {

    private final MediaService mediaService;

    MediaStorefrontProductImageLookup(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Override
    public Map<UUID, List<Image>> findByProductIds(Collection<UUID> productIds) {
        Map<UUID, List<Image>> result = new LinkedHashMap<>();
        mediaService.listProductImagesByProductIds(productIds).forEach((productId, images) ->
                result.put(productId, images.stream().map(this::toImage).toList()));
        return result;
    }

    private Image toImage(ProductImageDto image) {
        return new Image(
                image.asset().url(),
                image.asset().contentType(),
                image.asset().width(),
                image.asset().height(),
                image.position(),
                image.isPrimary());
    }
}
