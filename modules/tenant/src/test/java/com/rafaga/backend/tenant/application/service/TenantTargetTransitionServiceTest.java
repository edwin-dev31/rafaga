package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.port.DeploymentTargetHealthProbe;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.TenantTargetTransitionState;
import com.rafaga.backend.tenant.domain.model.TenantTargetTransitionView;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryState;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantTargetTransitionEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantTargetTransitionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TenantTargetTransitionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T19:00:00Z");

    private TenantTargetTransitionRepository transitions;
    private TenantDomainControlRepository domains;
    private DeploymentTargetRepository targets;
    private TenantDirectoryStateRepository directoryState;
    private DeploymentTargetHealthProbe healthProbe;
    private ApplicationEventPublisher events;
    private TenantTargetTransitionService service;
    private AtomicReference<TenantTargetTransitionEntity> stored;

    @BeforeEach
    void setUp() {
        transitions = mock(TenantTargetTransitionRepository.class);
        domains = mock(TenantDomainControlRepository.class);
        targets = mock(DeploymentTargetRepository.class);
        directoryState = mock(TenantDirectoryStateRepository.class);
        healthProbe = mock(DeploymentTargetHealthProbe.class);
        events = mock(ApplicationEventPublisher.class);
        stored = new AtomicReference<>();

        AtomicLong version = new AtomicLong(10);
        TenantDirectoryState state = mock(TenantDirectoryState.class);
        when(state.advance(any())).thenAnswer(inv -> version.incrementAndGet());
        when(directoryState.findBySingletonId((short) 1)).thenReturn(Optional.of(state));

        when(transitions.save(any())).thenAnswer(inv -> {
            TenantTargetTransitionEntity e = inv.getArgument(0);
            stored.set(e);
            return e;
        });
        when(transitions.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));

        service = new TenantTargetTransitionService(
                transitions, domains, targets, directoryState, healthProbe, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void executesPrepareValidateActivateAndRollbackWithAtomicDirectoryUpdates() {
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-vip", "vip.example.test", DomainStatus.ACTIVE, "https://vip.pages.dev", "shared-pool", NOW);
        DeploymentTargetEntity vipTarget = new DeploymentTargetEntity(
                "vip-1", DeploymentTargetType.DEDICATED_INSTANCE, "http", "http://vip-1:3117", "/actuator/health/readiness", NOW);
        vipTarget.mark(DeploymentTargetStatus.HEALTHY, DeploymentTargetCapacity.AVAILABLE, NOW);

        when(domains.findByTenantIdAndDomain("tenant-vip", "vip.example.test")).thenReturn(Optional.of(domain));
        when(targets.findById("vip-1")).thenReturn(Optional.of(vipTarget));
        when(healthProbe.isHealthy("http", "http://vip-1:3117", "/actuator/health/readiness")).thenReturn(true);

        TenantTargetTransitionView prepared = service.prepare("tenant-vip", "vip.example.test", "vip-1", "admin");
        assertThat(prepared.state()).isEqualTo(TenantTargetTransitionState.PREPARED);
        assertThat(domain.getTargetId()).isEqualTo("shared-pool");

        TenantTargetTransitionView validated = service.validateData("tenant-vip", prepared.id(), "sha256:abc123");
        assertThat(validated.state()).isEqualTo(TenantTargetTransitionState.VALIDATED);

        TenantTargetTransitionView activated = service.activate("tenant-vip", prepared.id());
        assertThat(activated.state()).isEqualTo(TenantTargetTransitionState.ACTIVATED);
        assertThat(domain.getTargetId()).isEqualTo("vip-1");

        TenantTargetTransitionView rolledBack = service.rollback("tenant-vip", prepared.id(), "Elevated p99 latency");
        assertThat(rolledBack.state()).isEqualTo(TenantTargetTransitionState.ROLLED_BACK);
        assertThat(rolledBack.failureReason()).isEqualTo("Elevated p99 latency");
        assertThat(domain.getTargetId()).isEqualTo("shared-pool");
        verify(events, times(2)).publishEvent(any(TenantDirectoryChangedEvent.class));
    }

    @Test
    void rejectsPreparingTransitionToUnhealthyTarget() {
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-vip", "vip.example.test", DomainStatus.ACTIVE, "https://vip.pages.dev", "shared-pool", NOW);
        DeploymentTargetEntity preparing = new DeploymentTargetEntity(
                "vip-down", DeploymentTargetType.DEDICATED_INSTANCE, "http", "http://vip:3117", "/health", NOW);

        when(domains.findByTenantIdAndDomain("tenant-vip", "vip.example.test")).thenReturn(Optional.of(domain));
        when(targets.findById("vip-down")).thenReturn(Optional.of(preparing));

        assertThatThrownBy(() -> service.prepare("tenant-vip", "vip.example.test", "vip-down", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not healthy/routable");
    }
}
