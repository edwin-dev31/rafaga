package com.rafaga.backend.identity.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import com.rafaga.backend.identity.infrastructure.keycloak.KeycloakProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KeycloakHealthIndicatorTest {
    @Test
    void reportsDownWhenTheIssuerIsNotConfigured() {
        KeycloakProperties properties = new KeycloakProperties();

        assertThat(new KeycloakHealthIndicator(properties, RestClient.create()).health().getStatus().getCode())
                .isEqualTo("DOWN");
    }
}
