package com.rafaga.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.tenant.application.port.TenantRegistry;
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
    private static final String REGISTRATION_TOKEN = "rafaga_test_registration_token";
    private static final Duration KEYCLOAK_STARTUP_TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRegistry tenantRegistry;

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
                "starter",
                TenantStatus.ACTIVE,
                Map.of("catalog", true)));
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
    void rejectsRegistrationWithoutTheGlobalRegistrationToken() throws Exception {
        mockMvc.perform(post("/api/public/auth/register")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
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
        String username = "store-owner-" + UUID.randomUUID();
        String email = username + "@example.test";
        String password = "SecurePassword123!";

        mockMvc.perform(post("/api/public/auth/register")
                        .with(request -> {
                            request.setServerName(TENANT_DOMAIN);
                            return request;
                        })
                        .header("X-Registration-Token", REGISTRATION_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "firstName", "Store",
                                "lastName", "Owner",
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
