package com.rafaga.backend.media.application.dto;

import java.util.UUID;

public record ProductImageDto(
    UUID id,
    UUID mediaAssetId,
    MediaAssetDto asset,
    int position,
    boolean isPrimary
) {}
