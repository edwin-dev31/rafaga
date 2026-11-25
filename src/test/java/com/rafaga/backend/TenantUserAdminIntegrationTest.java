package com.rafaga.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class TenantUserAdminIntegrationTest extends TenantIsolationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTenantUsersWithAbsentEmptyWhitespaceAndTextQueryWhileIsolatingTenants() throws Exception {
        String tenantId = "users-" + UUID.randomUUID().toString().substring(0, 16);
        String tenantHost = tenantId + ".isolation.test";
        String otherTenantId = "other-" + UUID.randomUUID().toString().substring(0, 16);
        String otherTenantHost = otherTenantId + ".isolation.test";
        tenants.register(isolationTenant(tenantId, tenantHost));
        tenants.register(isolationTenant(otherTenantId, otherTenantHost));

        seedLocalUser(tenantId, tenantHost, "kc-ana-" + UUID.randomUUID(), "ana.gomez", "ana@tienda.test");
        seedLocalUser(tenantId, tenantHost, "kc-luis-" + UUID.randomUUID(), "luis.perez", "luis@tienda.test");
        seedLocalUser(otherTenantId, otherTenantHost, "kc-other-" + UUID.randomUUID(), "ana.otra", "ana.otra@otra.test");

        // 1. Absent q (previously failed on PostgreSQL with function lower(bytea) does not exist)
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 2. Empty and whitespace q
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .param("q", "")
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .param("q", "   ")
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 3. Case-insensitive text search isolated to the target tenant
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .param("q", "ANA")
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("ana.gomez"));

        // 4. Status filter without search query
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .param("status", "ACTIVE")
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .param("status", "DISABLED")
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 5. STORE_MANAGER cannot list admin tenant users
        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", tenantId)
                        .with(storeManagerJwt(tenantId, "kc-mgr", "mgr", "mgr@tienda.test")))
                .andExpect(status().isForbidden());
    }

    private void seedLocalUser(
            String tenantId,
            String host,
            String keycloakId,
            String username,
            String email) throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .with(request -> {
                            request.setServerName(host);
                            return request;
                        })
                        .with(storeManagerJwt(tenantId, keycloakId, username, email)))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor superAdminJwt() {
        return jwt().jwt(token -> token.subject("kc-super-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private RequestPostProcessor storeManagerJwt(
            String tenantId,
            String keycloakId,
            String username,
            String email) {
        return jwt().jwt(token -> token
                        .subject(keycloakId)
                        .claim("preferred_username", username)
                        .claim("email", email)
                        .claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_STORE_MANAGER"));
    }
}
