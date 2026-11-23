package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.command.TenantUpdate;
import com.rafaga.backend.tenant.application.plan.PlanFeatureDefaults;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantStatusChangedEvent;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.rafaga.backend.tenant.infrastructure.web.TenantAdminController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantLifecycleAdminTest {

    @Test
    void preservesDedicatedDatabaseConfigWhenUpdateOmitsDatabaseConfigId() {
        TenantRegistry registry = mock(TenantRegistry.class);
        PlanFeatureDefaults features = mock(PlanFeatureDefaults.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantConfig existing = new TenantConfig(
                "tenant-ded", "Old Name", List.of("ded.test"), DeploymentMode.DEDICATED_DB,
                new DatabaseConfig("secret-db-ref"), "pro", TenantStatus.ACTIVE, Map.of());
        when(registry.findById("tenant-ded")).thenReturn(Optional.of(existing));

        TenantService service = new TenantService(registry, features, events);
        TenantConfig updated = service.updateTenant("tenant-ded",
                new TenantUpdate("New Name", List.of("ded.test"), DeploymentMode.DEDICATED_DB, null, "enterprise"));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.planId()).isEqualTo("enterprise");
        assertThat(updated.databaseConfig()).isEqualTo(new DatabaseConfig("secret-db-ref"));
    }

    @Test
    void deactivatesAndReactivatesTenantAndSyncsDomainRouting() {
        TenantRegistry registry = mock(TenantRegistry.class);
        PlanFeatureDefaults features = mock(PlanFeatureDefaults.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantDirectoryService directory = mock(TenantDirectoryService.class);

        TenantConfig active = new TenantConfig(
                "tenant-1", "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "pro", TenantStatus.ACTIVE, Map.of());
        TenantConfig inactive = new TenantConfig(
                "tenant-1", "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "pro", TenantStatus.INACTIVE, Map.of());
        when(registry.findById("tenant-1")).thenReturn(Optional.of(active), Optional.of(inactive));

        TenantService service = new TenantService(registry, features, events, null, directory);

        TenantConfig afterDeactivate = service.deactivateTenant("tenant-1");
        assertThat(afterDeactivate.status()).isEqualTo(TenantStatus.INACTIVE);
        verify(directory).suspendAllDomainsForTenant("tenant-1");
        verify(events).publishEvent(new TenantStatusChangedEvent("tenant-1", TenantStatus.ACTIVE, TenantStatus.INACTIVE));

        TenantConfig afterReactivate = service.reactivateTenant("tenant-1");
        assertThat(afterReactivate.status()).isEqualTo(TenantStatus.ACTIVE);
        verify(directory).reactivateSuspendedDomainsForTenant("tenant-1");
        verify(events).publishEvent(new TenantStatusChangedEvent("tenant-1", TenantStatus.INACTIVE, TenantStatus.ACTIVE));
    }

    @Test
    void exposesDeactivateAndReactivateEndpointsInTenantAdminController() throws Exception {
        TenantService service = mock(TenantService.class);
        when(service.deactivateTenant("tenant-1")).thenReturn(new TenantConfig(
                "tenant-1", "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "pro", TenantStatus.INACTIVE, Map.of()));
        when(service.reactivateTenant("tenant-1")).thenReturn(new TenantConfig(
                "tenant-1", "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "pro", TenantStatus.ACTIVE, Map.of()));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantAdminController(service)).build();

        mvc.perform(post("/api/admin/tenants/tenant-1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(post("/api/admin/tenants/tenant-1/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
