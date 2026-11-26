package com.rafaga.backend.identity.infrastructure.health;

import com.rafaga.backend.identity.infrastructure.keycloak.KeycloakProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KeycloakHealthIndicator implements HealthIndicator {
    private final KeycloakProperties properties;
    private final RestClient restClient;

    public KeycloakHealthIndicator(KeycloakProperties properties,
            @Qualifier("keycloakRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public Health health() {
        String issuerUri = properties.getIssuerUri();
        if (issuerUri == null || issuerUri.isBlank()) {
            return Health.down().withDetail("reason", "Keycloak issuer URI is not configured").build();
        }
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri(stripTrailingSlashes(issuerUri) + "/protocol/openid-connect/certs")
                    .retrieve()
                    .toBodilessEntity();
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up().withDetail("issuer", issuerUri).build();
            }
            return Health.down().withDetail("status", response.getStatusCode().value()).build();
        } catch (Exception exception) {
            return Health.down(exception).withDetail("issuer", issuerUri).build();
        }
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
