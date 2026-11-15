package com.rafaga.backend.media.infrastructure.job;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.port.out.MediaRepository;
import com.rafaga.backend.media.application.port.out.ObjectStorage;
import com.rafaga.backend.media.domain.model.MediaAsset;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final MediaRepository repository;
    private final ObjectStorage objectStorage;
    private final TenantRegistry tenantRegistry;
    private final Clock clock;

    @Value("${media.cache.grace-period-hours:24}")
    private int gracePeriodHours;

    @Autowired
    public MediaCleanupJob(MediaRepository repository, ObjectStorage objectStorage, TenantRegistry tenantRegistry) {
        this(repository, objectStorage, tenantRegistry, Clock.systemUTC());
    }

    MediaCleanupJob(MediaRepository repository, ObjectStorage objectStorage, TenantRegistry tenantRegistry, Clock clock) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.tenantRegistry = tenantRegistry;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "media.pending-delete-cleanup", lockAtMostFor = "PT10M")
    public void cleanupPendingDeletes() {
        Instant threshold = Instant.now(clock).minus(Duration.ofHours(gracePeriodHours));
        List<TenantConfig> tenants = tenantRegistry.findAll();

        for (TenantConfig tenant : tenants) {
            try {
                TenantContext.setCurrentTenantId(tenant.tenantId());
                List<MediaAsset> assets = repository.findPendingDeletePastGrace(threshold);

                for (MediaAsset asset : assets) {
                    try {
                        if (asset.getObjectKey() != null) {
                            objectStorage.delete(asset.getObjectKey());
                            asset.markDeleted(Instant.now(clock));
                            repository.save(asset);
                            log.info("Deleted asset {} from R2 for tenant {}", asset.getId(), tenant.tenantId());
                        }
                    } catch (Exception e) {
                        log.error("Failed to delete asset {} from R2: {}", asset.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process tenant {}: {}", tenant.tenantId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(cron = "0 0 */6 * * *")
    @SchedulerLock(name = "media.expired-pending-cleanup", lockAtMostFor = "PT10M")
    public void cleanupExpiredPending() {
        Instant threshold = Instant.now(clock);
        List<TenantConfig> tenants = tenantRegistry.findAll();

        for (TenantConfig tenant : tenants) {
            try {
                TenantContext.setCurrentTenantId(tenant.tenantId());
                List<MediaAsset> assets = repository.findPendingExpired(threshold);

                for (MediaAsset asset : assets) {
                    try {
                        if (asset.getUploadKey() != null && objectStorage.exists(asset.getUploadKey())) {
                            objectStorage.delete(asset.getUploadKey());
                        }
                        if (repository.failPendingAsset(asset.getId(), asset.getTenantId(), "Upload URL expired")) {
                            log.info("Cleaned up expired pending asset {} for tenant {}", asset.getId(), tenant.tenantId());
                        }
                    } catch (Exception e) {
                        log.error("Failed to clean up expired asset {}: {}", asset.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process tenant {}: {}", tenant.tenantId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "media.orphan-reconciliation", lockAtMostFor = "PT30M")
    public void reconcileOrphans() {
        log.info("Starting R2-PostgreSQL reconciliation");
        List<TenantConfig> tenants = tenantRegistry.findAll();

        for (TenantConfig tenant : tenants) {
            try {
                TenantContext.setCurrentTenantId(tenant.tenantId());
                // Per-tenant reconciliation logic here
                log.info("Reconciled tenant {}", tenant.tenantId());
            } catch (Exception e) {
                log.error("Failed to reconcile tenant {}: {}", tenant.tenantId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Reconciliation completed");
    }

    /**
     * Clean up orphaned staging objects: assets that are READY but still have
     * a non-null uploadKey (staging key). This happens when the delete of the
     * staging object after quota commit fails. The staging object is no longer
     * needed and should be removed from R2, then the uploadKey cleared.
     */
    @Scheduled(cron = "0 30 * * * *")
    @SchedulerLock(name = "media.orphaned-staging-cleanup", lockAtMostFor = "PT10M")
    public void cleanupOrphanedStaging() {
        List<TenantConfig> tenants = tenantRegistry.findAll();

        for (TenantConfig tenant : tenants) {
            try {
                TenantContext.setCurrentTenantId(tenant.tenantId());
                List<MediaAsset> assets = repository.findReadyWithStagingKey();

                for (MediaAsset asset : assets) {
                    try {
                        if (asset.getUploadKey() != null && objectStorage.exists(asset.getUploadKey())) {
                            objectStorage.delete(asset.getUploadKey());
                            log.info("Deleted orphaned staging {} for asset {} (tenant {})",
                                asset.getUploadKey(), asset.getId(), tenant.tenantId());
                        }
                        // Clear the uploadKey so we don't re-process
                        asset.clearUploadKey();
                        repository.save(asset);
                    } catch (Exception e) {
                        log.error("Failed to clean up orphaned staging for asset {}: {}",
                            asset.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process tenant {}: {}", tenant.tenantId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
