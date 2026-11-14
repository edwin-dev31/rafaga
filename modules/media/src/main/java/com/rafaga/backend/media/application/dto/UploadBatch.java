package com.rafaga.backend.media.application.dto;

import com.rafaga.backend.media.domain.model.AssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the batch upload workflow: requesting presigned URLs and receiving upload responses.
 */
public final class UploadBatch {

    private UploadBatch() {}

    public record Request(
        @NotNull(message = "assetType is required")
        AssetType assetType,
        @NotBlank(message = "contentType is required")
        String contentType,
        @PositiveOrZero(message = "estimatedSize must be zero or positive")
        long estimatedSize
    ) {}

    public record ItemResponse(
        String status,
        UUID assetId,
        String uploadKey,
        String uploadUrl,
        Instant expiresAt,
        Error error
    ) {}

    public record Response(
        List<ItemResponse> items,
        Summary summary
    ) {}

    public record Summary(
        int total,
        int accepted,
        int rejected
    ) {}

    public record Error(
        String code,
        String message
    ) {}
}
