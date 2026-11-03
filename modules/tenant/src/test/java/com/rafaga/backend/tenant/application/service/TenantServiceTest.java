package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.command.TenantRegistration;
import com.rafaga.backend.tenant.application.command.TenantUpdate;
import com.rafaga.backend.tenant.application.plan.PlanFeatureDefaults;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantCreatedEvent;
import com.rafaga.backend.tenant.domain.event.TenantStatusChangedEvent;
import com.rafaga.backend.tenant.domain.event.TenantUpdatedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TenantServiceTest {
    @Test
    void registersTenantWithDefaultsAndPublishesEvent() {
        TenantRegistry registry = mock(TenantRegistry.class);
        PlanFeatureDefaults defaults = mock(PlanFeatureDefaults.class);
        when(defaults.featuresFor("starter")).thenReturn(Map.of("catalog", true));
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantService service = new TenantService(registry, defaults, events);

        TenantConfig created = service.registerTenant(new TenantRegistration("Demo Store", List.of("demo.test"),
                DeploymentMode.SHARED_DB, null, "starter"));

        assertThat(created.tenantId()).isNotBlank();
        assertThat(created.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(created.featureFlags()).containsEntry("catalog", true);
        verify(registry).register(created);
        ArgumentCaptor<TenantCreatedEvent> event = ArgumentCaptor.forClass(TenantCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().tenantId()).isEqualTo(created.tenantId());
    }

    @Test
    void deactivatesTenantAndPublishesStatusChange() {
        TenantRegistry registry = mock(TenantRegistry.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantConfig active = new TenantConfig("tenant-1", "Demo Store", List.of("demo.test"),
                DeploymentMode.SHARED_DB, null, "starter", TenantStatus.ACTIVE, Map.of());
        when(registry.findById("tenant-1")).thenReturn(Optional.of(active));
        TenantService service = new TenantService(registry, planId -> Map.of(), events);

        TenantConfig deactivated = service.deactivateTenant("tenant-1");

        assertThat(deactivated.status()).isEqualTo(TenantStatus.INACTIVE);
        verify(registry).update(deactivated);
        verify(events).publishEvent(any(TenantStatusChangedEvent.class));
    }

    @Test
    void registersTenantsForEveryDeploymentMode() {
        TenantRegistry registry = mock(TenantRegistry.class);
        PlanFeatureDefaults defaults = planId -> Map.of();
        TenantService service = new TenantService(registry, defaults, mock(ApplicationEventPublisher.class));

        TenantConfig sharedTenant = service.registerTenant(registrationFor(DeploymentMode.SHARED_DB));
        TenantConfig dedicatedDatabaseTenant = service.registerTenant(registrationFor(DeploymentMode.DEDICATED_DB));
        TenantConfig dedicatedInstanceTenant = service.registerTenant(registrationFor(DeploymentMode.DEDICATED_INSTANCE));

        assertThat(sharedTenant.deploymentMode()).isEqualTo(DeploymentMode.SHARED_DB);
        assertThat(dedicatedDatabaseTenant.deploymentMode()).isEqualTo(DeploymentMode.DEDICATED_DB);
        assertThat(dedicatedDatabaseTenant.databaseConfig().secretReference())
                .isEqualTo("secrets/dedicated-database");
        assertThat(dedicatedInstanceTenant.deploymentMode()).isEqualTo(DeploymentMode.DEDICATED_INSTANCE);
    }

    @Test
    void updatesTenantAndPublishesTheChangedFields() {
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantConfig current = new TenantConfig(
                "tenant-1",
                "Demo Store",
                List.of("demo.test"),
                DeploymentMode.SHARED_DB,
                null,
                "starter",
                TenantStatus.ACTIVE,
                Map.of("analytics", false));
        when(registry.findById("tenant-1")).thenReturn(Optional.of(current));
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantService service = new TenantService(registry, planId -> Map.of(), events);

        TenantConfig updated = service.updateTenant(
                "tenant-1",
                new TenantUpdate(
                        "Renamed Store",
                        List.of("renamed.test"),
                        DeploymentMode.DEDICATED_DB,
                        "secrets/renamed-store",
                        "pro"));

        verify(registry).update(updated);
        ArgumentCaptor<TenantUpdatedEvent> event = ArgumentCaptor.forClass(TenantUpdatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().changes()).containsOnlyKeys(
                "name", "domains", "deploymentMode", "databaseConfigId", "planId");
    }

    private TenantRegistration registrationFor(DeploymentMode deploymentMode) {
        String databaseConfigId = deploymentMode == DeploymentMode.DEDICATED_DB
                ? "secrets/dedicated-database"
                : null;
        return new TenantRegistration(
                "Demo Store",
                List.of("demo.test"),
                deploymentMode,
                databaseConfigId,
                "starter");
    }
}
