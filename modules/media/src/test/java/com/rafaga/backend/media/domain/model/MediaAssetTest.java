package com.rafaga.backend.media.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaAssetTest {

    private static final String TENANT_ID = "tenant-1";
    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void newAsset_startsAsPending() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PENDING);
        assertThat(asset.getUploadKey()).isEqualTo("upload/key");
        assertThat(asset.getAssetType()).isEqualTo(AssetType.PRODUCT);
        assertThat(asset.getReservedBytes()).isEqualTo(5_242_880);
        assertThat(asset.getObjectKey()).isNull();
        assertThat(asset.getSizeBytes()).isNull();
    }

    @Test
    void markReady_setsFinalProperties() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );

        asset.markReady("final/key.webp", 800, 600, 5000, NOW);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(asset.getObjectKey()).isEqualTo("final/key.webp");
        assertThat(asset.getContentType()).isEqualTo("image/webp");
        assertThat(asset.getWidth()).isEqualTo(800);
        assertThat(asset.getHeight()).isEqualTo(600);
        assertThat(asset.getSizeBytes()).isEqualTo(5000);
    }

    @Test
    void markFailed_setsFailureInfo() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );

        asset.markFailed("MEDIA_INVALID_FORMAT", "Magic bytes no válidos", NOW);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.FAILED);
        assertThat(asset.getFailureCode()).isEqualTo("MEDIA_INVALID_FORMAT");
        assertThat(asset.getFailureReason()).isEqualTo("Magic bytes no válidos");
    }

    @Test
    void markPendingDelete_setsDeleteRequestedAt() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);

        asset.markPendingDelete(NOW);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PENDING_DELETE);
        assertThat(asset.getDeleteRequestedAt()).isEqualTo(NOW);
    }

    @Test
    void markDeleted_setsDeletedAt() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);
        asset.markPendingDelete(NOW);

        asset.markDeleted(NOW);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        assertThat(asset.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void cancelDeletion_restoresReadyFromPendingDelete() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);
        asset.markPendingDelete(NOW);

        asset.cancelDeletion(NOW);

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(asset.getDeleteRequestedAt()).isNull();
    }

    @Test
    void clearUploadKey_clearsStagingKey() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );

        asset.clearUploadKey();

        assertThat(asset.getUploadKey()).isNull();
        assertThat(asset.getUploadExpiresAt()).isNull();
    }

    @Test
    void fromDb_rehydratesFullAsset() {
        MediaAsset asset = MediaAsset.fromDb(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW,
            "final/key.webp", "image/webp", 5000L, 800, 600,
            AssetStatus.READY, null, null, NOW, null, null
        );

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(asset.getObjectKey()).isEqualTo("final/key.webp");
        assertThat(asset.getContentType()).isEqualTo("image/webp");
        assertThat(asset.getSizeBytes()).isEqualTo(5000);
        assertThat(asset.getWidth()).isEqualTo(800);
        assertThat(asset.getHeight()).isEqualTo(600);
    }

    @Test
    void transitionFromPendingToReady_isAllowed() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );

        asset.markReady("final/key.webp", 800, 600, 5000, NOW);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
    }

    @Test
    void transitionFromPendingToFailed_isAllowed() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );

        asset.markFailed("CODE", "reason", NOW);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.FAILED);
    }

    @Test
    void transitionFromReadyToPending_isNotAllowed() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);

        assertThatThrownBy(() -> asset.markReady("another/key.webp", 800, 600, 5000, NOW))
            .isInstanceOf(InvalidAssetStateTransitionException.class);
    }

    @Test
    void transitionFromDeletedToReady_isNotAllowed() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);
        asset.markPendingDelete(NOW);
        asset.markDeleted(NOW);

        assertThatThrownBy(() -> asset.cancelDeletion(NOW))
            .isInstanceOf(InvalidAssetStateTransitionException.class);
    }

    @Test
    void transitionFromFailedToPendingDelete_isAllowed() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markFailed("CODE", "reason", NOW);

        asset.markPendingDelete(NOW);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PENDING_DELETE);
    }

    @Test
    void transitionFromPendingDeleteToReady_isAllowed() {
        MediaAsset asset = new MediaAsset(
            UUID.randomUUID(), TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);
        asset.markPendingDelete(NOW);

        asset.cancelDeletion(NOW);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
    }

    @Test
    void immutableFields_cannotBeChanged() {
        UUID id = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(
            id, TENANT_ID, "upload/key", NOW.plusSeconds(300),
            5_242_880, "image/jpeg", AssetType.PRODUCT, NOW
        );
        asset.markReady("final/key.webp", 800, 600, 5000, NOW);

        assertThat(asset.getId()).isEqualTo(id);
        assertThat(asset.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(asset.getDeclaredContentType()).isEqualTo("image/jpeg");
        assertThat(asset.getAssetType()).isEqualTo(AssetType.PRODUCT);
        assertThat(asset.getCreatedAt()).isEqualTo(NOW);
    }
}
