package com.rafaga.backend.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsExplicitProductionValues() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(validEnvironment());

        assertThat(validator.validate()).isEmpty();
    }

    @Test
    void rejectsWildcardsAndUntrustedProxyRangesWithoutEchoingSecrets() {
        MockEnvironment environment = validEnvironment()
                .withProperty("rafaga.web.allowed-origins", "https://shop.example.test,*")
                .withProperty("rafaga.web.trusted-proxy.cidrs", "0.0.0.0/0");

        assertThat(new ProductionConfigurationValidator(environment).validate())
                .contains("rafaga.web.allowed-origins must contain explicit HTTPS origins only",
                        "rafaga.web.trusted-proxy.cidrs must not trust all addresses")
                .doesNotContain("registration-token-value");
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("rafaga.identity.registration-token", "registration-token-value-with-32-characters")
                .withProperty("rafaga.tenant-directory.internal-token", "tenant-directory-internal-token-32-chars")
                .withProperty("rafaga.identity.keycloak.client-secret", "client-secret-value-with-enough-length")
                .withProperty("rafaga.identity.keycloak.admin-client-secret", "admin-secret-value-with-enough-length")
                .withProperty("media.r2.account-id", "account")
                .withProperty("media.r2.access-key-id", "access")
                .withProperty("media.r2.secret-access-key", "r2-secret-value-with-enough-length")
                .withProperty("rafaga.identity.keycloak.issuer-uri", "https://auth.example.test/realms/rafaga")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "https://auth.example.test/certs")
                .withProperty("media.r2.public-base-url", "https://media.example.test")
                .withProperty("spring.data.redis.host", "10.0.0.10")
                .withProperty("rafaga.web.allowed-origins", "https://dashboard.example.test")
                .withProperty("rafaga.web.trusted-proxy.cidrs", "10.0.0.0/8");
    }
}
