package com.rafaga.backend.inventory.infrastructure.scheduling;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryScheduler {
    private final TenantRegistry tenants;
    private final StockService stock;
    private final Clock clock;

    @Autowired
    public ReservationExpiryScheduler(TenantRegistry tenants, StockService stock) {
        this(tenants, stock, Clock.systemUTC());
    }

    ReservationExpiryScheduler(TenantRegistry tenants, StockService stock, Clock clock) {
        this.tenants = tenants;
        this.stock = stock;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "inventory.reservation-expiry", lockAtMostFor = "PT5M")
    public void expireReservations() {
        for (TenantConfig tenant : tenants.findAll()) {
            if (tenant.status() != TenantStatus.ACTIVE) continue;
            TenantContext.setCurrentTenantId(tenant.tenantId());
            try {
                for (ReservationView reservation : stock.getExpiredReservations(clock.instant())) {
                    stock.expireReservation(reservation.id());
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}
