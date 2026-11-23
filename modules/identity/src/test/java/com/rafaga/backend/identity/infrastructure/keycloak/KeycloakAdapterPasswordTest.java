package com.rafaga.backend.identity.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

class KeycloakAdapterPasswordTest {

    @Test
    void sendsResetPasswordRequestToKeycloakAdminApi() throws Exception {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicInteger statusToReturn = new AtomicInteger(204);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/realms/rafaga/protocol/openid-connect/token", exchange -> {
            byte[] body = "{\"access_token\":\"admin-token-123\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/admin/realms/rafaga/users/kc-user-42/reset-password", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = statusToReturn.get();
            exchange.sendResponseHeaders(status, status == 204 ? -1 : 0);
            exchange.close();
        });
        server.start();
        try {
            KeycloakAdapter adapter = adapter("http://127.0.0.1:" + port + "/realms/rafaga");

            adapter.updatePassword("kc-user-42", "NewSecretPass123!", true);

            assertThat(capturedMethod.get()).isEqualTo("PUT");
            assertThat(capturedAuth.get()).isEqualTo("Bearer admin-token-123");
            assertThat(capturedBody.get())
                    .contains("\"type\":\"password\"")
                    .contains("\"value\":\"NewSecretPass123!\"")
                    .contains("\"temporary\":true");

            statusToReturn.set(400);
            assertThatThrownBy(() -> adapter.updatePassword("kc-user-42", "weak", false))
                    .isInstanceOf(PasswordPolicyViolationException.class);

            statusToReturn.set(404);
            assertThatThrownBy(() -> adapter.updatePassword("kc-user-42", "NewSecretPass123!", false))
                    .isInstanceOf(IdentityResourceNotFoundException.class);

            statusToReturn.set(503);
            assertThatThrownBy(() -> adapter.updatePassword("kc-user-42", "SecretShouldNotLeak!", false))
                    .isInstanceOf(IdentityProviderUnavailableException.class)
                    .hasMessageNotContaining("SecretShouldNotLeak!")
                    .hasMessageNotContaining("admin-secret-value");
        } finally {
            server.stop(0);
        }
    }

    private KeycloakAdapter adapter(String issuer) {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setIssuerUri(issuer);
        properties.setClientId("rafaga-backend");
        properties.setClientSecret("client-secret-value");
        properties.setAdminClientId("rafaga-admin");
        properties.setAdminClientSecret("admin-secret-value");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        RestClient restClient = new KeycloakClientConfiguration().keycloakRestClient(properties);
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        return new KeycloakAdapter(properties, decoderProvider, restClient);
    }
}
