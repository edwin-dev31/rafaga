package com.rafaga.backend.tenant.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.service.FeatureFlagService;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantFeaturesAdminControllerTest {

    @Test
    void inspectsUpdatesAndSyncsTenantFeatures() throws Exception {
        FeatureFlagService service = mock(FeatureFlagService.class);
        TenantConfig starter = new TenantConfig(
                "tenant-1",
                "Store 1",
                List.of("store1.test"),
                DeploymentMode.SHARED_DB,
                null,
                "starter",
                TenantStatus.ACTIVE,
                Map.of("catalog", true, "analytics", false));
        TenantConfig overridden = new TenantConfig(
                "tenant-1",
                "Store 1",
                List.of("store1.test"),
                DeploymentMode.SHARED_DB,
                null,
                "starter",
                TenantStatus.ACTIVE,
                Map.of("catalog", true, "analytics", true));

        when(service.getTenantFeatures("tenant-1")).thenReturn(starter);
        when(service.updateFeatureFlag("tenant-1", "analytics", true)).thenReturn(overridden);
        when(service.syncWithCurrentPlan("tenant-1")).thenReturn(starter);
        when(service.getTenantFeatures("missing")).thenThrow(new TenantNotFoundException("missing"));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantFeaturesAdminController(service))
                .setControllerAdvice(new TenantExceptionHandler())
                .build();

        mvc.perform(get("/api/admin/tenants/tenant-1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("starter"))
                .andExpect(jsonPath("$.featureFlags.analytics").value(false));

        mvc.perform(put("/api/admin/tenants/tenant-1/features/analytics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureFlags.analytics").value(true));

        mvc.perform(post("/api/admin/tenants/tenant-1/features/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureFlags.analytics").value(false));

        mvc.perform(get("/api/admin/tenants/missing/features"))
                .andExpect(status().isNotFound());
    }
}
