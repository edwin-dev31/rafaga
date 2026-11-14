package com.rafaga.backend.media.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images")
public class ProductImageEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "media_asset_id", nullable = false, columnDefinition = "uuid")
    private UUID mediaAssetId;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ProductImageEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
