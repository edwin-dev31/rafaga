package com.rafaga.backend.media.domain.model;

import java.time.Instant;
import java.util.UUID;

public class ProductImage {
    private final UUID id;
    private final String tenantId;
    private final UUID productId;
    private final UUID mediaAssetId;
    private final int position;
    private final boolean isPrimary;
    private final Instant createdAt;

    public ProductImage(UUID id, String tenantId, UUID productId, UUID mediaAssetId,
                        int position, boolean isPrimary, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.mediaAssetId = mediaAssetId;
        this.position = position;
        this.isPrimary = isPrimary;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public UUID getMediaAssetId() { return mediaAssetId; }
    public int getPosition() { return position; }
    public boolean isPrimary() { return isPrimary; }
    public Instant getCreatedAt() { return createdAt; }
}
