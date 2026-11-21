package com.rafaga.backend.inventory.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationExpirySchedulerTest {
    @Test
    void expiresReservationsUnderTheirTenantContext() {
        TenantRegistry tenants = mock(TenantRegistry.class);
        StockService stock = mock(StockService.class);
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(tenants.findAll()).thenReturn(List.of(new TenantConfig("tenant-a", "A", List.of(),
                DeploymentMode.SHARED_DB, null, "test", TenantStatus.ACTIVE, Map.of())));
        when(stock.getExpiredReservations(now)).thenAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");
            return List.of(new ReservationView(id, UUID.randomUUID(), null, 1, "order-1",
                    null, now, now.minusSeconds(1)));
        });
        when(stock.expireReservation(id)).thenAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");
            return null;
        });

        new ReservationExpiryScheduler(tenants, stock, Clock.fixed(now, ZoneOffset.UTC))
                .expireReservationsImmediately();

        verify(stock).expireReservation(id);
        assertThat(TenantContext.isSet()).isFalse();
    }
}
