package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryState;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperation;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TenantDirectoryServiceTest {
    private TenantDomainControlRepository domains;
    private TenantDirectoryStateRepository state;
    private DeploymentTargetRepository targets;
    private TenantDomainOperationRepository operations;
    private TenantRegistry tenantRegistry;
    private ApplicationEventPublisher events;
    private TenantDirectoryService service;

    @BeforeEach
    void setUp() {
        domains = mock(TenantDomainControlRepository.class);
        state = mock(TenantDirectoryStateRepository.class);
        targets = mock(DeploymentTargetRepository.class);
        operations = mock(TenantDomainOperationRepository.class);
        tenantRegistry = mock(TenantRegistry.class);
        events = mock(ApplicationEventPublisher.class);
        service = new TenantDirectoryService(domains, state, targets, operations, tenantRegistry, events);
    }

    @Test
    void snapshotEmitsRealTargetTypesAndReportsRemovedHostnamesInDelta() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        DeploymentTargetEntity shared = new DeploymentTargetEntity(
                "shared-pool", DeploymentTargetType.SHARED_POOL, "http", "http://backend:8080", "/actuator/health/readiness", now);
        shared.mark(DeploymentTargetStatus.HEALTHY, DeploymentTargetCapacity.AVAILABLE, now);

        DeploymentTargetEntity dedicated = new DeploymentTargetEntity(
                "vip-1", DeploymentTargetType.DEDICATED_INSTANCE, "http", "http://vip-1:8080", "/actuator/health/readiness", now);
        dedicated.mark(DeploymentTargetStatus.HEALTHY, DeploymentTargetCapacity.AVAILABLE, now);

        TenantDomainControl activeDedicated = new TenantDomainControl(
                "tenant-vip", "vip.example.test", DomainStatus.ACTIVE, "https://vip-ui.example.test", "vip-1", now);
        activeDedicated.configure("https://vip-ui.example.test", "vip-1", 4L, now);

        TenantDomainControl suspended = new TenantDomainControl(
                "tenant-old", "old.example.test", DomainStatus.ACTIVE, "https://old-ui.example.test", "shared-pool", now);
        suspended.transition(DomainStatus.SUSPENDED, 5L, now);

        TenantDirectoryState directoryState = mock(TenantDirectoryState.class);
        when(directoryState.getCurrentVersion()).thenReturn(5L);
        when(state.findById((short) 1)).thenReturn(Optional.of(directoryState));
        when(targets.findAll()).thenReturn(List.of(shared, dedicated));
        when(domains.findByDirectoryVersionGreaterThanOrderByDirectoryVersion(3L))
                .thenReturn(List.of(activeDedicated, suspended));

        TenantDirectorySnapshot delta = service.snapshot(3L);

        assertThat(delta.delta()).isTrue();
        assertThat(delta.directoryVersion()).isEqualTo(5L);
        assertThat(delta.activeEntries()).hasSize(1);
        assertThat(delta.activeEntries().getFirst().hostname()).isEqualTo("vip.example.test");
        assertThat(delta.activeEntries().getFirst().targetType()).isEqualTo(DeploymentTargetType.DEDICATED_INSTANCE);
        assertThat(delta.removedHostnames()).containsExactly("old.example.test");
    }

    @Test
    void idempotentRetryReturnsOriginalVersionWithoutPublishingDuplicateEvent() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-1", "shop.example.test", DomainStatus.ACTIVE, "https://shop-ui.example.test", "shared-pool", now);
        TenantDomainOperation completed = new TenantDomainOperation(
                "op-1", "tenant-1", "shop.example.test", "TRANSITION_SUSPENDED", "retry-key-1", now);
        completed.complete(9L, now);

        when(domains.findByTenantIdAndDomain("tenant-1", "shop.example.test")).thenReturn(Optional.of(domain));
        when(operations.findByTenantIdAndDomainAndTypeAndKey(
                "tenant-1", "shop.example.test", "TRANSITION_SUSPENDED", "retry-key-1"))
                .thenReturn(Optional.of(completed));

        long version = service.transition("tenant-1", "shop.example.test", DomainStatus.SUSPENDED, "retry-key-1");

        assertThat(version).isEqualTo(9L);
        verify(state, never()).findBySingletonId((short) 1);
        verify(events, never()).publishEvent(any(TenantDirectoryChangedEvent.class));
    }

    @Test
    void sameStateTransitionDoesNotAdvanceDirectoryVersion() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        TenantDomainControl domain = new TenantDomainControl(
                "tenant-1", "shop.example.test", DomainStatus.ACTIVE, "https://shop-ui.example.test", "shared-pool", now);
        domain.configure("https://shop-ui.example.test", "shared-pool", 7L, now);
        when(domains.findByTenantIdAndDomain("tenant-1", "shop.example.test")).thenReturn(Optional.of(domain));

        long version = service.transition("tenant-1", "shop.example.test", DomainStatus.ACTIVE, null);

        assertThat(version).isEqualTo(7L);
        verify(state, never()).findBySingletonId((short) 1);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void activationRequiresFrontendOriginAndRoutableTarget() {
        Instant now = Instant.parse("2026-11-21T12:00:00Z");
        TenantDomainControl missingOrigin = new TenantDomainControl(
                "tenant-1", "shop.example.test", DomainStatus.TARGET_PENDING, null, "shared-pool", now);
        when(domains.findByTenantIdAndDomain("tenant-1", "shop.example.test")).thenReturn(Optional.of(missingOrigin));

        assertThatThrownBy(() -> service.transition("tenant-1", "shop.example.test", DomainStatus.ACTIVE, "k1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frontendOrigin");

        TenantDomainControl unhealthyTargetDomain = new TenantDomainControl(
                "tenant-1", "shop2.example.test", DomainStatus.TARGET_PENDING, "https://ui.example.test", "vip-down", now);
        DeploymentTargetEntity unhealthy = new DeploymentTargetEntity(
                "vip-down", DeploymentTargetType.DEDICATED_INSTANCE, "http", "http://vip:8080", "/health", now);
        when(domains.findByTenantIdAndDomain("tenant-1", "shop2.example.test")).thenReturn(Optional.of(unhealthyTargetDomain));
        when(targets.findById("vip-down")).thenReturn(Optional.of(unhealthy));

        assertThatThrownBy(() -> service.transition("tenant-1", "shop2.example.test", DomainStatus.ACTIVE, "k2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not healthy/routable");
        verify(events, times(0)).publishEvent(any());
    }
}
