package com.rafaga.backend.tenant.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.service.TenantLimitsService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantLimitsAdminControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void returnsCurrentTenantLimits() throws Exception {
        TenantLimitsService service = mock(TenantLimitsService.class);
        when(service.getLimits("tenant-1")).thenReturn(PlanLimitsDefaults.forPlan("tenant-1", "pro", NOW));

        mockMvc(service).perform(get("/api/admin/tenants/tenant-1/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("pro"))
                .andExpect(jsonPath("$.maxProducts").value(500))
                .andExpect(jsonPath("$.maxAssets").value(2000))
                .andExpect(jsonPath("$.maxAdminUsers").value(3))
                .andExpect(jsonPath("$.maxDomains").value(2));
    }

    @Test
    void updatesTenantLimitsAndReturnsUpdatedValues() throws Exception {
        TenantLimitsService service = mock(TenantLimitsService.class);
        TenantLimits updated = new TenantLimits("tenant-1", "enterprise", 1000, 4000, 21474836480L, 6, 4, NOW);
        when(service.updateLimits("tenant-1", "enterprise", 1000, 4000L, 21474836480L, 6, 4))
                .thenReturn(updated);

        mockMvc(service).perform(put("/api/admin/tenants/tenant-1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planId": "enterprise",
                                  "maxProducts": 1000,
                                  "maxAssets": 4000,
                                  "maxStorageBytes": 21474836480,
                                  "maxAdminUsers": 6,
                                  "maxDomains": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value("enterprise"))
                .andExpect(jsonPath("$.maxProducts").value(1000))
                .andExpect(jsonPath("$.maxAssets").value(4000))
                .andExpect(jsonPath("$.maxAdminUsers").value(6));
    }

    @Test
    void syncsTenantLimitsWithCurrentPlan() throws Exception {
        TenantLimitsService service = mock(TenantLimitsService.class);
        when(service.syncWithCurrentPlan("tenant-1"))
                .thenReturn(PlanLimitsDefaults.forPlan("tenant-1", "starter", NOW));

        mockMvc(service).perform(post("/api/admin/tenants/tenant-1/limits/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxProducts").value(100))
                .andExpect(jsonPath("$.maxAssets").value(500));
    }

    @Test
    void returnsNotFoundForUnknownTenant() throws Exception {
        TenantLimitsService service = mock(TenantLimitsService.class);
        when(service.getLimits("missing")).thenThrow(new TenantNotFoundException("missing"));

        mockMvc(service).perform(get("/api/admin/tenants/missing/limits"))
                .andExpect(status().isNotFound());
    }

    private MockMvc mockMvc(TenantLimitsService service) {
        return MockMvcBuilders.standaloneSetup(new TenantLimitsAdminController(service))
                .setControllerAdvice(new TenantExceptionHandler())
                .build();
    }
}
