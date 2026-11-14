package com.rafaga.backend.media.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for the batch associate workflow: associating assets to products.
 */
public final class AssociateBatch {

    private AssociateBatch() {}

    public record Request(
        @NotNull(message = "assetId is required")
        UUID assetId,
        @Min(value = 0, message = "position must be zero or positive")
        int position,
        boolean isPrimary
    ) {}

    public record ItemResponse(
        UUID assetId,
        String status,
        UUID productImageId,
        Error error
    ) {}

    public record Response(
        String message,
        List<ItemResponse> results,
        Summary summary
    ) {}

    public record Summary(
        int total,
        int associated,
        int rejected
    ) {}

    public record Error(
        String code,
        String message
    ) {}
}
