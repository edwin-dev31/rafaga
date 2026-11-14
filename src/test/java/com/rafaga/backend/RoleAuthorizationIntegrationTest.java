package com.rafaga.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

class RoleAuthorizationIntegrationTest extends TenantIsolationTestBase {
    private static final String REGISTRATION_TOKEN = "rafaga_test_registration_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void storeManagerCanManageCatalogButCannotAdministerThePlatform() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(host(HOST_A))
                        .with(jwtFor(TENANT_A, "STORE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/tenants")
                        .with(host(HOST_A))
                        .with(jwtFor(TENANT_A, "STORE_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutABackofficeRoleCannotManageCatalog() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(host(HOST_A))
                        .with(jwt().jwt(token -> token.claim("tenant_id", TENANT_A))
                                .authorities(List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotAccessTheBackofficeProfileEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .with(host(HOST_A))
                        .with(jwtFor(TENANT_A, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlistedPublicEndpointsAreDeniedByDefault() throws Exception {
        mockMvc.perform(post("/api/public/unknown")
                        .with(host(HOST_A))
                        .with(jwtFor(TENANT_A, "STORE_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotManageCatalog() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(host(HOST_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storeManagerCannotRegisterUsers() throws Exception {
        mockMvc.perform(post("/api/public/auth/register")
                        .with(host(HOST_A))
                        .with(jwtFor(TENANT_A, "STORE_MANAGER"))
                        .header("X-Registration-Token", REGISTRATION_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "manager-" + UUID.randomUUID(),
                                "firstName", "Store",
                                "lastName", "Manager",
                                "email", "manager@example.test",
                                "password", "SecurePassword123!"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanRegisterAStoreManagerForAnotherTenant() throws Exception {
        String username = "cross-tenant-" + UUID.randomUUID();

        mockMvc.perform(post("/api/public/auth/register")
                        .with(host(HOST_B))
                        .with(jwtFor(TENANT_A, "SUPER_ADMIN"))
                        .header("X-Registration-Token", REGISTRATION_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "firstName", "Cross",
                                "lastName", "Tenant",
                                "email", username + "@example.test",
                                "password", "SecurePassword123!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));
    }

    private RequestPostProcessor host(String host) {
        return request -> {
            request.setServerName(host);
            return request;
        };
    }

    private RequestPostProcessor jwtFor(String tenantId, String role) {
        return jwt().jwt(token -> token.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
