package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantLimitsUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperationRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantPlanLimitsEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantPlanLimitsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TenantLimitsServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void providesExpectedDefaultsForStarterProAndEnterprisePlans() {
        TenantLimits starter = PlanLimitsDefaults.forPlan("t-1", "starter", NOW);
        assertThat(starter.maxProducts()).isEqualTo(100);
        assertThat(starter.maxAssets()).isEqualTo(500L);
        assertThat(starter.maxAdminUsers()).isEqualTo(1);
        assertThat(starter.maxDomains()).isEqualTo(1);

        TenantLimits pro = PlanLimitsDefaults.forPlan("t-2", "pro", NOW);
        assertThat(pro.maxProducts()).isEqualTo(500);
        assertThat(pro.maxAssets()).isEqualTo(2_000L);
        assertThat(pro.maxAdminUsers()).isEqualTo(3);
        assertThat(pro.maxDomains()).isEqualTo(2);

        TenantLimits enterprise = PlanLimitsDefaults.forPlan("t-3", "enterprise", NOW);
        assertThat(enterprise.maxProducts()).isEqualTo(1_000);
        assertThat(enterprise.maxAssets()).isEqualTo(4_000L);
        assertThat(enterprise.maxAdminUsers()).isEqualTo(5);
        assertThat(enterprise.maxDomains()).isEqualTo(4);
    }

    @Test
    void updatesPlanAndOverridesAndPublishesLimitsUpdatedEvent() {
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantPlanLimitsRepository repository = mock(TenantPlanLimitsRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantConfig starterTenant = new TenantConfig(
                "tenant-1", "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "starter", TenantStatus.ACTIVE, Map.of());
        when(registry.findById("tenant-1")).thenReturn(Optional.of(starterTenant));
        when(repository.findById("tenant-1")).thenReturn(Optional.empty());
        when(repository.save(any(TenantPlanLimitsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantLimitsService service = new TenantLimitsService(registry, repository, events, CLOCK);
        TenantLimits updated = service.updateLimits("tenant-1", "enterprise", null, null, null, 8, null);

        assertThat(updated.planId()).isEqualTo("enterprise");
        assertThat(updated.maxProducts()).isEqualTo(1_000);
        assertThat(updated.maxAssets()).isEqualTo(4_000L);
        assertThat(updated.maxAdminUsers()).isEqualTo(8);
        assertThat(updated.maxDomains()).isEqualTo(4);

        ArgumentCaptor<TenantConfig> tenantCaptor = ArgumentCaptor.forClass(TenantConfig.class);
        verify(registry).update(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().planId()).isEqualTo("enterprise");

        ArgumentCaptor<TenantLimitsUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(TenantLimitsUpdatedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().maxAssets()).isEqualTo(4_000L);
    }

    @Test
    void rejectsRequestingAdditionalDomainWhenTenantReachedMaxDomains() {
        TenantDomainControlRepository domains = mock(TenantDomainControlRepository.class);
        TenantDirectoryStateRepository state = mock(TenantDirectoryStateRepository.class);
        DeploymentTargetRepository targets = mock(DeploymentTargetRepository.class);
        TenantDomainOperationRepository operations = mock(TenantDomainOperationRepository.class);
        TenantRegistry registry = mock(TenantRegistry.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantLimitsService limitsService = mock(TenantLimitsService.class);

        TenantConfig tenant = new TenantConfig(
                "tenant-1", "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "starter", TenantStatus.ACTIVE, Map.of());
        when(registry.findById("tenant-1")).thenReturn(Optional.of(tenant));
        when(targets.existsById(TenantDirectoryService.DEFAULT_SHARED_POOL_TARGET_ID)).thenReturn(true);
        when(domains.findByDomain("extra.tienda.test")).thenReturn(Optional.empty());
        when(domains.countByTenantId("tenant-1")).thenReturn(1L);
        when(limitsService.getLimits("tenant-1")).thenReturn(PlanLimitsDefaults.forPlan("tenant-1", "starter", NOW));

        TenantDirectoryService directoryService = new TenantDirectoryService(
                domains, state, targets, operations, registry, events, limitsService);

        assertThatThrownBy(() -> directoryService.requestDomain(
                "tenant-1", "extra.tienda.test", null, null, null))
                .isInstanceOf(TenantLimitExceededException.class)
                .hasMessageContaining("domains");
    }
}
