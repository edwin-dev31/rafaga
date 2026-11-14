package com.rafaga.backend.media.infrastructure.job;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.port.out.MediaRepository;
import com.rafaga.backend.media.application.port.out.ObjectStorage;
import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.MediaAsset;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaCleanupJobTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    @Mock private MediaRepository repository;
    @Mock private ObjectStorage objectStorage;
    @Mock private TenantRegistry tenantRegistry;

    private MediaCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new MediaCleanupJob(repository, objectStorage, tenantRegistry, CLOCK);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void cleanupPendingDeletes_iteratesTenants() {
        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingDeletePastGrace(any(Instant.class))).thenReturn(List.of());

        job.cleanupPendingDeletes();

        verify(tenantRegistry).findAll();
        verify(repository).findPendingDeletePastGrace(any(Instant.class));
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void cleanupPendingDeletes_deletesAssetsFromR2() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.fromDb(
            assetId, "tenant-1", null, null, 5_242_880, "image/jpeg",
            com.rafaga.backend.media.domain.model.AssetType.PRODUCT, NOW,
            "final/key.webp", "image/webp", 5000L, 800, 600,
            AssetStatus.PENDING_DELETE, null, null, NOW, NOW, null
        );

        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingDeletePastGrace(any(Instant.class))).thenReturn(List.of(asset));

        job.cleanupPendingDeletes();

        verify(objectStorage).delete("final/key.webp");
        verify(repository).save(any(MediaAsset.class));
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
    }

    @Test
    void cleanupPendingDeletes_handlesStorageDeleteError() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.fromDb(
            assetId, "tenant-1", null, null, 5_242_880, "image/jpeg",
            com.rafaga.backend.media.domain.model.AssetType.PRODUCT, NOW,
            "final/key.webp", "image/webp", 5000L, 800, 600,
            AssetStatus.PENDING_DELETE, null, null, NOW, NOW, null
        );

        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingDeletePastGrace(any(Instant.class))).thenReturn(List.of(asset));
        doThrow(new RuntimeException("R2 error")).when(objectStorage).delete(anyString());

        job.cleanupPendingDeletes();

        // Should not throw; error is logged and processing continues
        verify(objectStorage).delete("final/key.webp");
        // Asset should NOT be marked deleted since storage delete failed
        verify(repository, never()).save(any(MediaAsset.class));
    }

    @Test
    void cleanupExpiredPending_iteratesTenants() {
        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingExpired(any(Instant.class))).thenReturn(List.of());

        job.cleanupExpiredPending();

        verify(tenantRegistry).findAll();
        verify(repository).findPendingExpired(any(Instant.class));
    }

    @Test
    void cleanupExpiredPending_deletesStagingAndReleasesQuota() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(
            assetId, "tenant-1", "upload/key", NOW.minusSeconds(3600),
            5_242_880, "image/jpeg", com.rafaga.backend.media.domain.model.AssetType.PRODUCT, NOW.minusSeconds(7200)
        );

        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingExpired(any(Instant.class))).thenReturn(List.of(asset));
        when(objectStorage.exists("upload/key")).thenReturn(true);

        job.cleanupExpiredPending();

        verify(objectStorage).delete("upload/key");
        verify(repository).releaseQuotaReservation("tenant-1", 5_242_880L);
        verify(repository).updateStatus(assetId, AssetStatus.FAILED, "Upload URL expired");
    }

    @Test
    void cleanupExpiredPending_handlesMissingStaging() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(
            assetId, "tenant-1", "upload/key", NOW.minusSeconds(3600),
            5_242_880, "image/jpeg", com.rafaga.backend.media.domain.model.AssetType.PRODUCT, NOW.minusSeconds(7200)
        );

        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingExpired(any(Instant.class))).thenReturn(List.of(asset));
        when(objectStorage.exists("upload/key")).thenReturn(false);

        job.cleanupExpiredPending();

        verify(objectStorage, never()).delete(anyString());
        verify(repository).releaseQuotaReservation("tenant-1", 5_242_880L);
        verify(repository).updateStatus(assetId, AssetStatus.FAILED, "Upload URL expired");
    }

    @Test
    void reconcileOrphans_iteratesTenants() {
        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));

        job.reconcileOrphans();

        verify(tenantRegistry).findAll();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void cleanupOrphanedStaging_deletesStagingFromReadyAssets() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.fromDb(
            assetId, "tenant-1", "upload/orphan", NOW, 5_242_880, "image/jpeg",
            com.rafaga.backend.media.domain.model.AssetType.PRODUCT, NOW,
            "final/key.webp", "image/webp", 5000L, 800, 600,
            AssetStatus.READY, null, null, NOW, null, null
        );

        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findReadyWithStagingKey()).thenReturn(List.of(asset));
        when(objectStorage.exists("upload/orphan")).thenReturn(true);

        job.cleanupOrphanedStaging();

        verify(objectStorage).delete("upload/orphan");
        verify(repository).save(any(MediaAsset.class));
        assertThat(asset.getUploadKey()).isNull();
    }

    @Test
    void cleanupOrphanedStaging_clearsUploadKeyEvenIfStagingMissing() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.fromDb(
            assetId, "tenant-1", "upload/orphan", NOW, 5_242_880, "image/jpeg",
            com.rafaga.backend.media.domain.model.AssetType.PRODUCT, NOW,
            "final/key.webp", "image/webp", 5000L, 800, 600,
            AssetStatus.READY, null, null, NOW, null, null
        );

        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findReadyWithStagingKey()).thenReturn(List.of(asset));
        when(objectStorage.exists("upload/orphan")).thenReturn(false);

        job.cleanupOrphanedStaging();

        verify(objectStorage, never()).delete(anyString());
        verify(repository).save(any(MediaAsset.class));
        assertThat(asset.getUploadKey()).isNull();
    }

    @Test
    void cleanupPendingDeletes_clearsTenantContextOnError() {
        TenantConfig tenant = new TenantConfig(
            "tenant-1", "tenant-1", List.of("test.com"),
            com.rafaga.backend.tenant.domain.model.DeploymentMode.SHARED_DB,
            null, "test", com.rafaga.backend.tenant.domain.model.TenantStatus.ACTIVE,
            java.util.Map.of("catalog", true)
        );
        when(tenantRegistry.findAll()).thenReturn(List.of(tenant));
        when(repository.findPendingDeletePastGrace(any(Instant.class)))
            .thenThrow(new RuntimeException("DB error"));

        job.cleanupPendingDeletes();

        // TenantContext should be cleared even after error
        assertThat(TenantContext.isSet()).isFalse();
    }
}
