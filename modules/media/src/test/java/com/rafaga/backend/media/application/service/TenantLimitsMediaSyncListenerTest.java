package com.rafaga.backend.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.port.out.MediaRepository;
import com.rafaga.backend.tenant.domain.event.TenantLimitsUpdatedEvent;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantLimitsMediaSyncListenerTest {
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void synchronizesMediaQuotaLimitsOnTenantLimitsUpdatedEvent() {
        MediaRepository repository = mock(MediaRepository.class);
        TenantLimitsMediaSyncListener listener = new TenantLimitsMediaSyncListener(repository);

        TenantContext.setCurrentTenantId("other-tenant");
        listener.onTenantLimitsUpdated(new TenantLimitsUpdatedEvent(
                "tenant-1", "enterprise", 1000, 4000L, 21_474_836_480L, 5, 4, Instant.now()));

        verify(repository).updateQuotaLimits("tenant-1", 4000L, 21_474_836_480L);
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("other-tenant");
    }
}
