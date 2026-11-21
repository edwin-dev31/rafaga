package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.JobOutcomeRegistry;
import com.rafaga.backend.tenant.application.port.CloudflareHostnamePort;
import com.rafaga.backend.tenant.application.port.CloudflareProvisioningException;
import com.rafaga.backend.tenant.application.port.CustomHostnameStatus;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.DomainOperationState;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryState;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DomainActivationWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-21T17:00:00Z");

    private TenantDomainControlRepository domains;
    private TenantDirectoryStateRepository directoryState;
    private DeploymentTargetRepository targets;
    private TenantDomainOperationRepository operations;
    private CloudflareHostnamePort cloudflare;
    private ApplicationEventPublisher events;
    private JobOutcomeRegistry jobOutcomes;
    private DomainActivationWorker worker;
    private AtomicLong versionCounter;

    @BeforeEach
    void setUp() {
        domains = mock(TenantDomainControlRepository.class);
        directoryState = mock(TenantDirectoryStateRepository.class);
        targets = mock(DeploymentTargetRepository.class);
        operations = mock(TenantDomainOperationRepository.class);
        cloudflare = mock(CloudflareHostnamePort.class);
        events = mock(ApplicationEventPublisher.class);
        jobOutcomes = new JobOutcomeRegistry();
        versionCounter = new AtomicLong(1);

        TenantDirectoryState state = mock(TenantDirectoryState.class);
        when(state.advance(any())).thenAnswer(inv -> versionCounter.incrementAndGet());
        when(directoryState.findBySingletonId((short) 1)).thenReturn(Optional.of(state));
        when(operations.findByTenantIdAndDomainAndTypeAndKey(any(), any(), any(), any())).thenReturn(Optional.empty());

        worker = new DomainActivationWorker(
                domains, directoryState, targets, operations, cloudflare, events, jobOutcomes,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void advancesDomainThroughFullCloudflareActivationLifecycle() {
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-1", "shop.example.test", DomainStatus.REQUESTED, "https://ui.example.test", "shared-pool", NOW);
        DeploymentTargetEntity healthyTarget = new DeploymentTargetEntity(
                "shared-pool", DeploymentTargetType.SHARED_POOL, "http", "http://backend:8080", "/health", NOW);
        healthyTarget.mark(DeploymentTargetStatus.HEALTHY, DeploymentTargetCapacity.AVAILABLE, NOW);
        when(targets.findById("shared-pool")).thenReturn(Optional.of(healthyTarget));

        when(cloudflare.createOrGetCustomHostname("shop.example.test")).thenReturn(
                new CustomHostnameStatus("cf-123", "shop.example.test", false, false, false, null, "_cf.shop", "tok"));

        worker.processSingleDomain(domain, NOW);
        assertThat(domain.getStatus()).isEqualTo(DomainStatus.OWNERSHIP_PENDING);
        assertThat(domain.getExternalHostnameId()).isEqualTo("cf-123");

        when(cloudflare.getCustomHostname("cf-123", "shop.example.test")).thenReturn(
                Optional.of(new CustomHostnameStatus("cf-123", "shop.example.test", true, false, false, null, null, null)));
        worker.processSingleDomain(domain, NOW.plusSeconds(30));
        assertThat(domain.getStatus()).isEqualTo(DomainStatus.CERTIFICATE_PENDING);

        when(cloudflare.getCustomHostname("cf-123", "shop.example.test")).thenReturn(
                Optional.of(new CustomHostnameStatus("cf-123", "shop.example.test", true, true, false, null, null, null)));
        worker.processSingleDomain(domain, NOW.plusSeconds(60));
        assertThat(domain.getStatus()).isEqualTo(DomainStatus.TARGET_PENDING);

        worker.processSingleDomain(domain, NOW.plusSeconds(90));
        assertThat(domain.getStatus()).isEqualTo(DomainStatus.ACTIVE);
        assertThat(domain.getActivatedAt()).isEqualTo(NOW.plusSeconds(90));
        verify(events, org.mockito.Mockito.times(4)).publishEvent(any(TenantDirectoryChangedEvent.class));
    }

    @Test
    void schedulesExponentialBackoffOnRetryableErrorAndFailsSafelyOnNonRetryableError() {
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-1", "retry.example.test", DomainStatus.REQUESTED, "https://ui.example.test", "shared-pool", NOW);
        when(cloudflare.createOrGetCustomHostname("retry.example.test"))
                .thenThrow(new CloudflareProvisioningException("CLOUDFLARE_HTTP_503", "Service unavailable", true));

        worker.processSingleDomain(domain, NOW);
        assertThat(domain.getStatus()).isEqualTo(DomainStatus.REQUESTED);
        assertThat(domain.getOperationState()).isEqualTo(DomainOperationState.RETRY_SCHEDULED);
        assertThat(domain.getRetryCount()).isEqualTo(1);
        assertThat(domain.getNextRetryAt()).isEqualTo(NOW.plusSeconds(30));

        org.mockito.Mockito.doThrow(new CloudflareProvisioningException("CLOUDFLARE_HTTP_403", "Forbidden", false))
                .when(cloudflare).createOrGetCustomHostname("retry.example.test");
        worker.processSingleDomain(domain, NOW.plusSeconds(31));
        assertThat(domain.getStatus()).isEqualTo(DomainStatus.FAILED);
        assertThat(domain.getLastFailureCode()).isEqualTo("CLOUDFLARE_HTTP_403");
        assertThat(domain.getNextRetryAt()).isNull();
    }

    @Test
    void removesExternalHostnameAndDeletesControlPlaneRecordWhenRemoving() {
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-1", "old.example.test", DomainStatus.ACTIVE, "https://ui.example.test", "shared-pool", NOW);
        domain.recordExternalHostnameId("cf-999", NOW);
        domain.transition(DomainStatus.REMOVING, 3L, NOW);
        when(domains.findByStatusInOrderByRequestedAtAsc(any())).thenReturn(List.of(domain));

        int processed = worker.processDueDomains();

        assertThat(processed).isEqualTo(1);
        verify(cloudflare).deleteCustomHostname("cf-999");
        verify(domains).delete(domain);
    }
}
