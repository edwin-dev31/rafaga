package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.inventory.infrastructure.scheduling.ReservationExpiryScheduler;
import com.rafaga.backend.media.infrastructure.job.MediaCleanupJob;
import java.lang.reflect.Method;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class ScheduledJobLockConfigurationTest {
    @Test
    void assignsUniqueDistributedLocksToEveryMutatingScheduledJob() throws Exception {
        Map<Method, String> jobs = Map.of(
                ReservationExpiryScheduler.class.getMethod("expireReservations"), "inventory.reservation-expiry",
                MediaCleanupJob.class.getMethod("cleanupPendingDeletes"), "media.pending-delete-cleanup",
                MediaCleanupJob.class.getMethod("cleanupExpiredPending"), "media.expired-pending-cleanup",
                MediaCleanupJob.class.getMethod("reconcileOrphans"), "media.orphan-reconciliation",
                MediaCleanupJob.class.getMethod("cleanupOrphanedStaging"), "media.orphaned-staging-cleanup");

        jobs.forEach((method, expectedName) -> {
            SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
            assertThat(lock).isNotNull();
            assertThat(lock.name()).isEqualTo(expectedName);
            assertThat(lock.lockAtMostFor()).isNotBlank();
        });
    }
}
