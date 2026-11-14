package com.rafaga.backend.media.application.dto;

import java.util.UUID;

public record MediaAssetDto(
    UUID id,
    String url,
    String contentType,
    Long sizeBytes,
    Integer width,
    Integer height,
    String status,
    String assetType,
    String createdAt
) {}
