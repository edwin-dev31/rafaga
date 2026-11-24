package com.rafaga.backend.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.media.application.MediaOperations;
import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import com.rafaga.backend.tenant.application.TenantOperationsQuery;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantCapabilitiesControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsCapabilitiesLimitsAndEffectiveUsageForCurrentTenant() throws Exception {
        TenantContext.setCurrentTenantId("tenant-pro");
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantLimitsQuery limitsQuery = mock(TenantLimitsQuery.class);
        ProductService productService = mock(ProductService.class);
        UserService userService = mock(UserService.class);
        MediaOperations mediaOperations = mock(MediaOperations.class);
        TenantOperationsQuery operationsQuery = mock(TenantOperationsQuery.class);

        when(registry.findById("tenant-pro")).thenReturn(Optional.of(new TenantConfig(
                "tenant-pro", "Tienda Pro", List.of("pro.test"), DeploymentMode.SHARED_DB,
                null, "pro", TenantStatus.ACTIVE, Map.of("catalog", true, "analytics", true))));
        when(limitsQuery.getLimits("tenant-pro")).thenReturn(PlanLimitsDefaults.forPlan("tenant-pro", "pro", NOW));
        when(productService.countProducts()).thenReturn(42L);
        when(userService.countUsers()).thenReturn(2L);
        when(mediaOperations.quotaFor("tenant-pro"))
                .thenReturn(new MediaOperations.TenantQuota(2000L, 5_368_709_120L, 100L, 50_000_000L, 5L, 2_000_000L));
        when(operationsQuery.tenantSummary("tenant-pro")).thenReturn(new TenantOperationsQuery.TenantRouteOperationsSummary(
                List.of(new TenantOperationsQuery.DomainLifecycleSummary(
                        "pro.test", "ACTIVE", "shared-pool", "SHARED_POOL", "HEALTHY", "AVAILABLE", false))));

        MockMvc mvc = mockMvc(registry, limitsQuery, productService, userService, mediaOperations, operationsQuery);

        mvc.perform(get("/api/tenant/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("pro"))
                .andExpect(jsonPath("$.featureFlags.analytics").value(true))
                .andExpect(jsonPath("$.limits.products.used").value(42))
                .andExpect(jsonPath("$.limits.products.limit").value(500))
                .andExpect(jsonPath("$.limits.products.remaining").value(458))
                .andExpect(jsonPath("$.limits.products.exceeded").value(false))
                .andExpect(jsonPath("$.limits.assets.used").value(105))
                .andExpect(jsonPath("$.limits.assets.limit").value(2000))
                .andExpect(jsonPath("$.limits.storageBytes.used").value(52_000_000))
                .andExpect(jsonPath("$.limits.adminUsers.used").value(2))
                .andExpect(jsonPath("$.limits.domains.used").value(1))
                .andExpect(jsonPath("$.downgradePolicy").value(TenantCapabilitiesController.DOWNGRADE_POLICY));
    }

    @Test
    void reportsExceededUsageCleanlyAfterDowngradeToLowerPlan() throws Exception {
        TenantContext.setCurrentTenantId("tenant-down");
        TenantRegistry registry = mock(TenantRegistry.class);
        TenantLimitsQuery limitsQuery = mock(TenantLimitsQuery.class);
        ProductService productService = mock(ProductService.class);
        UserService userService = mock(UserService.class);
        MediaOperations mediaOperations = mock(MediaOperations.class);

        when(registry.findById("tenant-down")).thenReturn(Optional.of(new TenantConfig(
                "tenant-down", "Tienda Starter", List.of("a.test", "b.test"), DeploymentMode.SHARED_DB,
                null, "starter", TenantStatus.ACTIVE, Map.of("catalog", true, "analytics", false))));
        when(limitsQuery.getLimits("tenant-down")).thenReturn(PlanLimitsDefaults.forPlan("tenant-down", "starter", NOW));
        when(productService.countProducts()).thenReturn(150L);
        when(userService.countUsers()).thenReturn(2L);
        when(mediaOperations.quotaFor("tenant-down"))
                .thenReturn(new MediaOperations.TenantQuota(500L, 1_073_741_824L, 600L, 2_000_000_000L, 0L, 0L));

        MockMvc mvc = mockMvc(registry, limitsQuery, productService, userService, mediaOperations, null);

        mvc.perform(get("/api/tenant/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("starter"))
                .andExpect(jsonPath("$.limits.products.used").value(150))
                .andExpect(jsonPath("$.limits.products.limit").value(100))
                .andExpect(jsonPath("$.limits.products.remaining").value(0))
                .andExpect(jsonPath("$.limits.products.exceeded").value(true))
                .andExpect(jsonPath("$.limits.assets.exceeded").value(true))
                .andExpect(jsonPath("$.limits.adminUsers.exceeded").value(true))
                .andExpect(jsonPath("$.limits.domains.exceeded").value(true));
    }

    @SuppressWarnings("unchecked")
    private MockMvc mockMvc(
            TenantRegistry registry,
            TenantLimitsQuery limitsQuery,
            ProductService productService,
            UserService userService,
            MediaOperations mediaOperations,
            TenantOperationsQuery operationsQuery) {
        ObjectProvider<MediaOperations> mediaProvider = mock(ObjectProvider.class);
        when(mediaProvider.getIfAvailable()).thenReturn(mediaOperations);
        ObjectProvider<TenantOperationsQuery> opsProvider = mock(ObjectProvider.class);
        when(opsProvider.getIfAvailable()).thenReturn(operationsQuery);
        return MockMvcBuilders.standaloneSetup(new TenantCapabilitiesController(
                registry, limitsQuery, productService, userService, mediaProvider, opsProvider)).build();
    }
}
