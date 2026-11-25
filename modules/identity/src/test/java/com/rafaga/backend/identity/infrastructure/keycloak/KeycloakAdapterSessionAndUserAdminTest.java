package com.rafaga.backend.identity.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

class KeycloakAdapterSessionAndUserAdminTest {

    @Test
    void refreshesLogsOutAndTogglesUserEnabledStateInKeycloak() throws Exception {
        AtomicReference<String> tokenRequestBody = new AtomicReference<>();
        AtomicReference<String> logoutRequestBody = new AtomicReference<>();
        AtomicReference<String> userUpdateBody = new AtomicReference<>();
        AtomicBoolean adminSessionRevoked = new AtomicBoolean(false);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/realms/rafaga/protocol/openid-connect/token", exchange -> {
            String req = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequestBody.set(req);
            byte[] body = "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/realms/rafaga/protocol/openid-connect/logout", exchange -> {
            logoutRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/admin/realms/rafaga/users/kc-99", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/logout")) {
                adminSessionRevoked.set(true);
            } else {
                userUpdateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            KeycloakAdapter adapter = adapter("http://127.0.0.1:" + port + "/realms/rafaga");

            AuthenticationTokens refreshed = adapter.refresh("old-refresh");
            assertThat(refreshed.accessToken()).isEqualTo("new-access");
            assertThat(refreshed.refreshToken()).isEqualTo("new-refresh");
            assertThat(tokenRequestBody.get()).contains("grant_type=refresh_token").contains("refresh_token=old-refresh");

            adapter.logout("new-refresh");
            assertThat(logoutRequestBody.get()).contains("refresh_token=new-refresh");

            adapter.setUserEnabled("kc-99", false);
            assertThat(userUpdateBody.get()).contains("\"enabled\":false");
            assertThat(adminSessionRevoked.get()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsAndReplacesManagedRealmRolesWithoutTouchingDefaultKeycloakRoles() throws Exception {
        AtomicInteger adminTokenRequests = new AtomicInteger();
        AtomicReference<String> deletedRolesBody = new AtomicReference<>();
        AtomicReference<String> addedRolesBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/realms/rafaga/protocol/openid-connect/token", exchange -> {
            adminTokenRequests.incrementAndGet();
            byte[] body = "{\"access_token\":\"admin-token\",\"refresh_token\":\"\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/admin/realms/rafaga/users/kc-99/role-mappings/realm", exchange -> {
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                byte[] body = "[{\"id\":\"r1\",\"name\":\"STORE_MANAGER\"},{\"id\":\"r0\",\"name\":\"default-roles-rafaga\"}]"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } else if ("DELETE".equals(method)) {
                deletedRolesBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            } else if ("POST".equals(method)) {
                addedRolesBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
        });
        server.createContext("/admin/realms/rafaga/users/kc-missing/role-mappings/realm", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/admin/realms/rafaga/roles/CUSTOMER", exchange -> {
            byte[] body = "{\"id\":\"r2\",\"name\":\"CUSTOMER\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            KeycloakAdapter adapter = adapter("http://127.0.0.1:" + port + "/realms/rafaga");

            Map<String, List<String>> batch = adapter.getUserRolesBatch(List.of("kc-99", "kc-missing"));
            assertThat(batch.get("kc-99")).containsExactly("STORE_MANAGER");
            assertThat(batch.get("kc-missing")).isEmpty();
            assertThat(adminTokenRequests.get()).isEqualTo(1);

            List<String> updated = adapter.replaceUserRoles("kc-99", List.of("CUSTOMER"));
            assertThat(updated).containsExactly("CUSTOMER");
            assertThat(deletedRolesBody.get()).contains("STORE_MANAGER").doesNotContain("default-roles-rafaga");
            assertThat(addedRolesBody.get()).contains("CUSTOMER");
        } finally {
            server.stop(0);
        }
    }

    private KeycloakAdapter adapter(String issuer) {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setIssuerUri(issuer);
        properties.setClientId("rafaga-backend");
        properties.setClientSecret("client-secret");
        properties.setAdminClientId("rafaga-admin");
        properties.setAdminClientSecret("admin-secret");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        RestClient restClient = new KeycloakClientConfiguration().keycloakRestClient(properties);
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        return new KeycloakAdapter(properties, decoderProvider, restClient);
    }
}
