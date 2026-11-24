package com.rafaga.backend.identity.infrastructure.security;

import com.rafaga.backend.identity.infrastructure.web.CorsProperties;
import com.rafaga.backend.identity.infrastructure.keycloak.KeycloakProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(KeycloakProperties properties,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        if (properties.getIssuerUri() == null || properties.getIssuerUri().isBlank()
                || properties.getClientId() == null || properties.getClientId().isBlank()) {
            throw new IllegalStateException("Keycloak issuer URI and client ID must be configured for JWT validation");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(restTemplate)
                .build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.getIssuerUri());
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(properties.getClientId())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT audience is invalid", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/api/internal/tenant-directory/**").permitAll()
                        .requestMatchers("/api/public/auth/login", "/api/public/auth/refresh", "/api/public/auth/logout").permitAll()
                        .requestMatchers("/api/public/auth/register").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/public/**").denyAll()
                        .requestMatchers("/api/storefront/**").permitAll()
                        .requestMatchers("/api/media/**").hasAnyRole("SUPER_ADMIN", "STORE_MANAGER")
                        .requestMatchers("/api/tenant/**").hasAnyRole("SUPER_ADMIN", "STORE_MANAGER")
                        .requestMatchers("/actuator/**", "/api/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/users/me", "/api/users/me/**").hasAnyRole("SUPER_ADMIN", "STORE_MANAGER")
                        .requestMatchers("/api/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/products/**", "/api/categories/**", "/api/stock/**",
                                "/api/orders/**", "/api/carts/**")
                                .hasAnyRole("SUPER_ADMIN", "STORE_MANAGER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties,
            ObjectProvider<com.rafaga.backend.tenant.application.port.TenantRegistry> registryProvider) {
        // Dynamic CORS source that resolves origins per tenant for storefront requests
        return new CorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Registration-Token", "X-Tenant-ID"));
                configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                // For storefront requests, resolve origins from tenant configuration
                String path = request.getRequestURI();
                if (path.startsWith("/api/storefront/")) {
                    com.rafaga.backend.tenant.application.port.TenantRegistry registry = registryProvider.getIfAvailable();
                    if (registry != null) {
                        String serverName = request.getServerName();
                        registry.findByDomain(serverName)
                                .flatMap(tenant -> tenant.domains().stream().findFirst())
                                .ifPresent(domain -> {
                                    String origin = "https://" + domain;
                                    configuration.setAllowedOrigins(List.of(origin));
                                });
                    }
                }
                // Fallback to global allowed origins
                if (configuration.getAllowedOrigins() == null || configuration.getAllowedOrigins().isEmpty()) {
                    configuration.setAllowedOrigins(properties.getAllowedOrigins());
                }
                return configuration;
            }
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::authoritiesFrom);
        return converter;
    }

    private Collection<GrantedAuthority> authoritiesFrom(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(stringValues(jwt.getClaim("roles")));
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> claims) {
            roles.addAll(stringValues(claims.get("roles")));
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Collection<String> stringValues(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return Set.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }
}
