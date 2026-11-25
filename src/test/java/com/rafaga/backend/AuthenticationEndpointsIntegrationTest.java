package com.rafaga.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.application.service.TenantLimitsService;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationEndpointsIntegrationTest {
    private static final String TENANT_ID = "tenant-demo";
    private static final String TENANT_DOMAIN = "demo.rafaga.test";
    private static final String SECOND_TENANT_ID = "tenant-two";
    private static final String SECOND_TENANT_DOMAIN = "two.rafaga.test";
    private static final String REGISTRATION_TOKEN = "rafaga_test_registration_token";
    private static final Duration KEYCLOAK_STARTUP_TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRegistry tenantRegistry;

    @Autowired
    private TenantLimitsService tenantLimitsService;

    @BeforeAll
    static void waitForKeycloak() throws InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        Instant deadline = Instant.now().plus(KEYCLOAK_STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (isKeycloakReady(client)) {
                return;
            }
            Thread.sleep(Duration.ofSeconds(1));
        }
        throw new IllegalStateException("Keycloak did not become ready within " + KEYCLOAK_STARTUP_TIMEOUT);
    }

    @BeforeEach
    void registerTenantForAuthenticationRequests() {
        tenantRegistry.register(new TenantConfig(
                TENANT_ID,
                "Demo Store",
                List.of(TENANT_DOMAIN),
                DeploymentMode.SHARED_DB,
                null,
                "pro",
                TenantStatus.ACTIVE,
                Map.of("catalog", true)));
        tenantLimitsService.updateLimits(TENANT_ID, "pro", null, null, null, 100, null);
        tenantRegistry.register(new TenantConfig(
                SECOND_TENANT_ID,
                "Second Store",
                List.of(SECOND_TENANT_DOMAIN),
                DeploymentMode.SHARED_DB,
                null,
                "pro",
                TenantStatus.ACTIVE,
                Map.of("catalog", true)));
        tenantLimitsService.updateLimits(SECOND_TENANT_ID, "pro", null, null, null, 100, null);
    }

    @Test
    void authenticatesTheImportedKeycloakUserThroughTheBackendEndpoint() throws Exception {
        MvcResult result = login("platform-admin", "rafaga_dev_password");

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(response.path("accessToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(response.path("refreshToken").asText()).isNotBlank();
    }

    @Test
    void rejectsInvalidCredentialsWithoutExposingKeycloak() throws Exception {
        mockMvc.perform(post("/api/public/auth/login")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "platform-admin",
                                "password", "invalid-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRegistrationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/public/auth/register")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .header("X-Registration-Token", REGISTRATION_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "unregistered-user",
                                "firstName", "Unregistered",
                                "lastName", "User",
                                "email", "unregistered-user@example.test",
                                "password", "SecurePassword123!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRegistrationWithAnInvalidTokenForTheSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/public/auth/register")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .header("Authorization", "Bearer " + accessToken())
                        .header("X-Registration-Token", "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "unregistered-user",
                                "firstName", "Unregistered",
                                "lastName", "User",
                                "email", "unregistered-user@example.test",
                                "password", "SecurePassword123!"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsTheConfiguredStaticFrontendOriginForAuthenticationEndpoints() throws Exception {
        mockMvc.perform(options("/api/public/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void registersAuthenticatesAndSynchronizesANewUserForTheDomainTenant() throws Exception {
        String username = "store-manager-" + UUID.randomUUID();
        String email = username + "@example.test";
        String password = "SecurePassword123!";

        mockMvc.perform(post("/api/public/auth/register")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .header("Authorization", "Bearer " + accessToken())
                        .header("X-Registration-Token", REGISTRATION_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "firstName", "Store",
                                "lastName", "Manager",
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email));

        JsonNode loginResponse = objectMapper.readTree(login(username, password).getResponse().getContentAsString());
        mockMvc.perform(get("/api/users/me")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .header("Authorization", "Bearer " + loginResponse.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void superAdminCanRegisterAStoreManagerForAnotherTenant() throws Exception {
        String username = "cross-tenant-manager-" + UUID.randomUUID();

        mockMvc.perform(post("/api/public/auth/register")
                        .with(request -> {
                            request.setServerName(SECOND_TENANT_DOMAIN);
                            return request;
                        })
                        .header("Authorization", "Bearer " + accessToken())
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

    @Test
    void superAdminCanResetPasswordForSelectedTenantUserAndRejectsWrongTenant() throws Exception {
        String resetTenantId = "pwd-" + UUID.randomUUID().toString().substring(0, 16);
        String resetTenantDomain = resetTenantId + ".rafaga.test";
        tenantRegistry.register(new TenantConfig(
                resetTenantId,
                "Password Reset Store",
                List.of(resetTenantDomain),
                DeploymentMode.SHARED_DB,
                null,
                "pro",
                TenantStatus.ACTIVE,
                Map.of("catalog", true)));
        tenantLimitsService.updateLimits(resetTenantId, "pro", null, null, null, 100, null);

        String adminToken = accessToken();
        String username = "reset-target-" + UUID.randomUUID();
        String initialPassword = "InitialPassword123!";
        String rotatedPassword = "RotatedPassword456!";

        MvcResult created = mockMvc.perform(post("/api/admin/tenants/{tenantId}/users", resetTenantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "firstName", "Target",
                                "lastName", "Manager",
                                "email", username + "@example.test",
                                "password", initialPassword,
                                "role", "STORE_MANAGER"))))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        // Cross-tenant reset attempt must fail with 404 without mutating Keycloak password
        mockMvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/password", TENANT_ID, userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", rotatedPassword))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Reset under the selected tenant from dashboard (no store Host header required)
        mockMvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/password", resetTenantId, userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newPassword", rotatedPassword,
                                "temporary", false))))
                .andExpect(status().isNoContent());

        // Old password fails, rotated password succeeds
        mockMvc.perform(post("/api/public/auth/login")
                        .with(request -> {
                            request.setServerName(resetTenantDomain);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", initialPassword))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/public/auth/login")
                        .with(request -> {
                            request.setServerName(resetTenantDomain);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", rotatedPassword))))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanListAndUpdateTenantUserRolesInKeycloak() throws Exception {
        String rolesTenantId = "roles-" + UUID.randomUUID().toString().substring(0, 16);
        String rolesTenantDomain = rolesTenantId + ".rafaga.test";
        tenantRegistry.register(new TenantConfig(
                rolesTenantId,
                "Roles Store",
                List.of(rolesTenantDomain),
                DeploymentMode.SHARED_DB,
                null,
                "pro",
                TenantStatus.ACTIVE,
                Map.of("catalog", true)));
        tenantLimitsService.updateLimits(rolesTenantId, "pro", null, null, null, 100, null);

        String adminToken = accessToken();
        String username = "role-user-" + UUID.randomUUID();
        String password = "RolePassword123!";

        MvcResult created = mockMvc.perform(post("/api/admin/tenants/{tenantId}/users", rolesTenantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "firstName", "Role",
                                "lastName", "Target",
                                "email", username + "@example.test",
                                "password", password,
                                "role", "CUSTOMER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andReturn();
        String userId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", rolesTenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roles[0]").value("CUSTOMER"));

        mockMvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/roles", rolesTenantId, userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"UNKNOWN_ROLE\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/tenants/{tenantId}/users/{userId}/roles", rolesTenantId, userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"STORE_MANAGER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STORE_MANAGER"));

        mockMvc.perform(get("/api/admin/tenants/{tenantId}/users", rolesTenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roles[0]").value("STORE_MANAGER"));

        MvcResult loginRes = mockMvc.perform(post("/api/public/auth/login")
                        .with(request -> {
                            request.setServerName(rolesTenantDomain);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        String managerToken = objectMapper.readTree(loginRes.getResponse().getContentAsString())
                .path("accessToken").asText();

        mockMvc.perform(get("/api/users/me")
                        .with(request -> {
                            request.setServerName(rolesTenantDomain);
                            return request;
                        })
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STORE_MANAGER"));
    }

    @Test
    void refreshesAndLogsOutSessionAndRejectsRefreshAfterDeactivation() throws Exception {
        String sessionTenantId = "sess-" + UUID.randomUUID().toString().substring(0, 16);
        String sessionTenantDomain = sessionTenantId + ".rafaga.test";
        tenantRegistry.register(new TenantConfig(
                sessionTenantId,
                "Session Store",
                List.of(sessionTenantDomain),
                DeploymentMode.SHARED_DB,
                null,
                "pro",
                TenantStatus.ACTIVE,
                Map.of("catalog", true)));
        tenantLimitsService.updateLimits(sessionTenantId, "pro", null, null, null, 100, null);

        String adminToken = accessToken();
        String username = "session-mgr-" + UUID.randomUUID();
        String password = "SessionPassword123!";

        MvcResult created = mockMvc.perform(post("/api/admin/tenants/{tenantId}/users", sessionTenantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "firstName", "Session",
                                "lastName", "Manager",
                                "email", username + "@example.test",
                                "password", password,
                                "role", "STORE_MANAGER"))))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();

        MvcResult loginResult = mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(post("/api/public/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        String nextRefreshToken = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .path("refreshToken").asText();

        mockMvc.perform(post("/api/public/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", nextRefreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/public/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", nextRefreshToken))))
                .andExpect(status().isUnauthorized());

        MvcResult secondLogin = mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        String activeRefreshToken = objectMapper.readTree(secondLogin.getResponse().getContentAsString())
                .path("refreshToken").asText();

        mockMvc.perform(post("/api/admin/tenants/{tenantId}/users/{userId}/deactivate", sessionTenantId, userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(post("/api/public/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", activeRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private String accessToken() throws Exception {
        JsonNode response = objectMapper.readTree(
                login("platform-admin", "rafaga_dev_password").getResponse().getContentAsString());
        return response.path("accessToken").asText();
    }

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/public/auth/login")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static boolean isKeycloakReady(HttpClient client) {
        try {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(keycloakDiscoveryUri())
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static URI keycloakDiscoveryUri() {
        String port = System.getenv().getOrDefault("TEST_KEYCLOAK_PORT", "8081");
        return URI.create("http://localhost:" + port + "/realms/rafaga/.well-known/openid-configuration");
    }
}
