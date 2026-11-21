package com.rafaga.backend.inventory.infrastructure.scheduling;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.application.JobOutcomeRegistry;
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
    private final JobOutcomeRegistry outcomes;

    @Autowired
    public ReservationExpiryScheduler(TenantRegistry tenants, StockService stock, JobOutcomeRegistry outcomes) {
        this(tenants, stock, Clock.systemUTC(), outcomes);
    }

    ReservationExpiryScheduler(TenantRegistry tenants, StockService stock, Clock clock) {
        this(tenants, stock, clock, new JobOutcomeRegistry());
    }

    ReservationExpiryScheduler(TenantRegistry tenants, StockService stock, Clock clock, JobOutcomeRegistry outcomes) {
        this.tenants = tenants;
        this.stock = stock;
        this.clock = clock;
        this.outcomes = outcomes;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "inventory.reservation-expiry", lockAtMostFor = "PT5M")
    public void expireReservations() {
        expireReservationsImmediately();
    }

    /**
     * Runs the expiration workflow synchronously when the caller already controls execution.
     * Scheduled production executions must use {@link #expireReservations()} so ShedLock
     * coordinates the job across backend instances.
     */
    public void expireReservationsImmediately() {
        outcomes.execute("inventory.reservation-expiry", this::expireReservationsOnce);
    }

    private void expireReservationsOnce() {
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
