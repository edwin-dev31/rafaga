package com.rafaga.backend.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.infrastructure.keycloak.KeycloakAdapter;
import com.rafaga.backend.identity.infrastructure.keycloak.KeycloakClientConfiguration;
import com.rafaga.backend.identity.infrastructure.keycloak.KeycloakProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestClient;

/**
 * Verifies section 6.2 and 6.3:
 * - Valid existing JWTs continue to authorize requests when Keycloak becomes unavailable.
 * - Expired, wrong-issuer, wrong-audience, and unknown/revoked-key JWTs fail safely.
 * - Login fails fast with bounded timeouts and returns 503 without leaking credentials.
 */
class KeycloakResilienceAndJwtHardeningTest {

    @Test
    void validExistingJwtContinuesWorkingWhenKeycloakGoesDownWhileExpiredAndRevokedTokensFailSafely() throws Exception {
        RSAKey activeKey = new RSAKeyGenerator(2048).keyID("active-kid-1").generate();
        RSAKey revokedKey = new RSAKeyGenerator(2048).keyID("revoked-kid-2").generate();
        String jwksJson = new JWKSet(activeKey.toPublicJWK()).toString();

        HttpServer jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = jwksServer.getAddress().getPort();
        jwksServer.createContext("/realms/rafaga/protocol/openid-connect/certs", exchange -> {
            byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        jwksServer.start();

        String issuer = "http://127.0.0.1:" + port + "/realms/rafaga";
        String jwksUri = issuer + "/protocol/openid-connect/certs";
        KeycloakProperties properties = keycloakProperties(issuer);

        JwtDecoder decoder = new SecurityConfig().jwtDecoder(properties, jwksUri);

        // Warm JWKS cache while Keycloak is up
        String initialValidToken = signToken(activeKey, issuer, "rafaga-backend", Instant.now().plusSeconds(300));
        Jwt decodedOnline = decoder.decode(initialValidToken);
        assertThat(decodedOnline.getSubject()).isEqualTo("manager-1");

        // Simulate complete Keycloak outage
        jwksServer.stop(0);

        // 1. Existing unexpired JWT signed by cached key MUST still validate
        String anotherValidToken = signToken(activeKey, issuer, "rafaga-backend", Instant.now().plusSeconds(120));
        Jwt decodedOffline = decoder.decode(anotherValidToken);
        assertThat(decodedOffline.getSubject()).isEqualTo("manager-1");
        assertThat(decodedOffline.getAudience()).contains("rafaga-backend");

        // 2. Expired JWT MUST fail safely
        String expiredToken = signToken(activeKey, issuer, "rafaga-backend", Instant.now().minusSeconds(120));
        assertThatThrownBy(() -> decoder.decode(expiredToken))
                .isInstanceOf(JwtException.class);

        // 3. Wrong audience JWT MUST fail safely
        String wrongAudienceToken = signToken(activeKey, issuer, "other-client", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(wrongAudienceToken))
                .isInstanceOf(JwtException.class);

        // 4. Wrong issuer JWT MUST fail safely
        String wrongIssuerToken = signToken(activeKey, "https://evil.example/realms/rafaga", "rafaga-backend",
                Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(wrongIssuerToken))
                .isInstanceOf(JwtException.class);

        // 5. Unknown/revoked signing key MUST fail safely (cannot verify while Keycloak is down)
        String revokedKeyToken = signToken(revokedKey, issuer, "rafaga-backend", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(revokedKeyToken))
                .isInstanceOf(JwtException.class);

        // 6. Login against unavailable Keycloak MUST fail fast with IdentityProviderUnavailableException
        RestClient restClient = new KeycloakClientConfiguration().keycloakRestClient(properties);
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        KeycloakAdapter adapter = new KeycloakAdapter(properties, decoderProvider, restClient);

        assertThatThrownBy(() -> adapter.login("manager-secret-user", "super-secret-password"))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageNotContaining("super-secret-password")
                .hasMessageNotContaining("client-secret-value");
    }

    private static KeycloakProperties keycloakProperties(String issuer) {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setIssuerUri(issuer);
        properties.setClientId("rafaga-backend");
        properties.setClientSecret("client-secret-value");
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setReadTimeout(Duration.ofMillis(500));
        return properties;
    }

    private static String signToken(RSAKey key, String issuer, String audience, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("manager-1")
                .issuer(issuer)
                .audience(List.of(audience))
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(expiresAt))
                .claim("preferred_username", "manager")
                .claim("tenant_id", "tenant-1")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
