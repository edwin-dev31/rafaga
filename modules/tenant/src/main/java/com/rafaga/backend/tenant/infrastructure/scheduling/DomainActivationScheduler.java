package com.rafaga.backend.tenant.infrastructure.scheduling;

import com.rafaga.backend.tenant.application.service.DomainActivationWorker;
import com.rafaga.backend.tenant.infrastructure.cloudflare.CloudflareProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Coordinates periodic domain activation across replicas using PostgreSQL ShedLock. */
@Component
public class DomainActivationScheduler {
    private final DomainActivationWorker worker;
    private final CloudflareProperties cloudflareProperties;

    public DomainActivationScheduler(DomainActivationWorker worker, CloudflareProperties cloudflareProperties) {
        this.worker = worker;
        this.cloudflareProperties = cloudflareProperties;
    }

    @Scheduled(fixedDelayString = "${rafaga.cloudflare.activation-poll-interval:30s}")
    @SchedulerLock(name = "tenant.domain-activation", lockAtMostFor = "PT5M")
    public void processPendingDomains() {
        if (!cloudflareProperties.isEnabled()) {
            return;
        }
        worker.processDueDomains();
    }
}
