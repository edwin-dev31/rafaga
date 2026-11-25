package com.rafaga.backend.identity.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.service.UserProfile;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.identity.domain.model.UserStatus;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantUserAdminControllerTest {
    private static final String TENANT_ID = "tenant-1";

    @Test
    void listsAndSearchesUsersWithinTenant() throws Exception {
        TenantRegistry registry = activeRegistry();
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        when(service.listTenantUsers(eq(TENANT_ID), eq("ana"), eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new UserProfile(userId, "ana.store", "ana@tienda.test", UserStatus.ACTIVE)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc(registry, service).perform(get("/api/admin/tenants/{tenantId}/users", TENANT_ID)
                        .param("q", "ana")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("ana.store"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void listsUsersWhenSearchQueryIsAbsentOrBlank() throws Exception {
        TenantRegistry registry = activeRegistry();
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        PageImpl<UserProfile> page = new PageImpl<>(
                List.of(new UserProfile(userId, "ana.store", "ana@tienda.test", UserStatus.ACTIVE)),
                PageRequest.of(0, 20),
                1);
        when(service.listTenantUsers(eq(TENANT_ID), eq(null), eq(null), any())).thenReturn(page);
        when(service.listTenantUsers(eq(TENANT_ID), eq(""), eq(null), any())).thenReturn(page);
        when(service.listTenantUsers(eq(TENANT_ID), eq("   "), eq(null), any())).thenReturn(page);

        MockMvc mvc = mockMvc(registry, service);
        mvc.perform(get("/api/admin/tenants/{tenantId}/users", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(userId.toString()));

        mvc.perform(get("/api/admin/tenants/{tenantId}/users", TENANT_ID).param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("ana.store"));

        mvc.perform(get("/api/admin/tenants/{tenantId}/users", TENANT_ID).param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("ana.store"));
    }

    @Test
    void registersTenantManagerWithoutRegistrationTokenHeader() throws Exception {
        TenantRegistry registry = activeRegistry();
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        when(service.registerTenantUser(eq(TENANT_ID), any(), eq("STORE_MANAGER")))
                .thenReturn(new UserProfile(userId, "manager1", "manager1@tienda.test", UserStatus.ACTIVE));

        mockMvc(registry, service).perform(post("/api/admin/tenants/{tenantId}/users", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "manager1",
                                  "firstName": "Ana",
                                  "lastName": "Gomez",
                                  "email": "manager1@tienda.test",
                                  "password": "StrongPassword123!",
                                  "role": "STORE_MANAGER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("manager1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activatesAndDeactivatesTenantUser() throws Exception {
        TenantRegistry registry = activeRegistry();
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        when(service.setTenantUserStatus(TENANT_ID, userId, UserStatus.DISABLED))
                .thenReturn(new UserProfile(userId, "manager1", "manager1@tienda.test", UserStatus.DISABLED));
        when(service.setTenantUserStatus(TENANT_ID, userId, UserStatus.ACTIVE))
                .thenReturn(new UserProfile(userId, "manager1", "manager1@tienda.test", UserStatus.ACTIVE));

        mockMvc(registry, service).perform(post("/api/admin/tenants/{tenantId}/users/{userId}/deactivate", TENANT_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc(registry, service).perform(post("/api/admin/tenants/{tenantId}/users/{userId}/activate", TENANT_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void resetsTenantUserPasswordAndRejectsInvalidOrCrossTenantRequests() throws Exception {
        TenantRegistry registry = activeRegistry();
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        UUID otherTenantUserId = UUID.randomUUID();
        doThrow(new IdentityResourceNotFoundException("User", otherTenantUserId))
                .when(service).resetTenantUserPassword(TENANT_ID, otherTenantUserId, "StrongPassword123!", false);

        MockMvc mvc = mockMvc(registry, service);
        mvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/password", TENANT_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"StrongPassword123!\"}"))
                .andExpect(status().isNoContent());
        verify(service).resetTenantUserPassword(TENANT_ID, userId, "StrongPassword123!", false);

        mvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/password", TENANT_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"StrongPassword123!\",\"temporary\":true}"))
                .andExpect(status().isNoContent());
        verify(service).resetTenantUserPassword(TENANT_ID, userId, "StrongPassword123!", true);

        mvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/password", TENANT_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/password", TENANT_ID, otherTenantUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"StrongPassword123!\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsRolesInUserListAndUpdatesUserRolesWithValidation() throws Exception {
        TenantRegistry registry = activeRegistry();
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        when(service.listTenantUsers(eq(TENANT_ID), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new UserProfile(
                                userId, "ana.store", "ana@tienda.test", UserStatus.ACTIVE, List.of("STORE_MANAGER"))),
                        PageRequest.of(0, 20),
                        1));
        when(service.updateTenantUserRoles(TENANT_ID, userId, List.of("STORE_MANAGER")))
                .thenReturn(new UserProfile(
                        userId, "ana.store", "ana@tienda.test", UserStatus.ACTIVE, List.of("STORE_MANAGER")));

        MockMvc mvc = mockMvc(registry, service);
        mvc.perform(get("/api/admin/tenants/{tenantId}/users", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roles[0]").value("STORE_MANAGER"));

        mvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/roles", TENANT_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"STORE_MANAGER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STORE_MANAGER"));

        mvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/roles", TENANT_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsNotFoundForUnknownTenantAndConflictOnPlanLimitExceeded() throws Exception {
        TenantRegistry registry = activeRegistry();
        when(registry.findById("missing")).thenReturn(Optional.empty());
        UserService service = mock(UserService.class);
        when(service.registerTenantUser(eq(TENANT_ID), any(), any()))
                .thenThrow(new TenantLimitExceededException("adminUsers", 1, 1));

        mockMvc(registry, service).perform(get("/api/admin/tenants/missing/users"))
                .andExpect(status().isNotFound());

        mockMvc(registry, service).perform(post("/api/admin/tenants/{tenantId}/users", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "manager2",
                                  "firstName": "Luis",
                                  "lastName": "Perez",
                                  "email": "manager2@tienda.test",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    private TenantRegistry activeRegistry() {
        TenantRegistry registry = mock(TenantRegistry.class);
        when(registry.findById(TENANT_ID)).thenReturn(Optional.of(new TenantConfig(
                TENANT_ID, "Tienda", List.of("tienda.test"), DeploymentMode.SHARED_DB,
                null, "pro", TenantStatus.ACTIVE, Map.of())));
        return registry;
    }

    private MockMvc mockMvc(TenantRegistry registry, UserService service) {
        return MockMvcBuilders.standaloneSetup(new TenantUserAdminController(registry, service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }
}
