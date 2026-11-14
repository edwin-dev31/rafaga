package com.rafaga.backend.media.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for the batch confirm workflow: confirming uploaded assets and receiving processing results.
 */
public final class ConfirmBatch {

    private ConfirmBatch() {}

    public record ItemResponse(
        UUID assetId,
        String status,
        MediaAssetDto asset,
        Error error
    ) {}

    public record Response(
        String message,
        List<ItemResponse> results,
        Summary summary
    ) {}

    public record Summary(
        int total,
        int ready,
        int failed
    ) {}

    public record Error(
        String code,
        String message,
        Long optimizedSizeBytes,
        Long maxOutputSizeBytes
    ) {}
}
