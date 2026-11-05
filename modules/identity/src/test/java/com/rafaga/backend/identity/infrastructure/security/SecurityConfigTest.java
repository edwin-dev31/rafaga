package com.rafaga.backend.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SecurityConfigTest {
    @Test
    void mapsKeycloakRealmRolesToSpringRoleAuthorities() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "user-1", "realm_access", Map.of("roles", List.of("PLATFORM_ADMIN"))));
        JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

        assertThat(converter.convert(jwt).getAuthorities())
                .extracting("authority")
                .contains("ROLE_PLATFORM_ADMIN");
    }
}
