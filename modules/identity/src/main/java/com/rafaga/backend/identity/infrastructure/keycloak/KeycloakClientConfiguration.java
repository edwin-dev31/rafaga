package com.rafaga.backend.identity.infrastructure.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Bounded HTTP client shared by Keycloak authentication and health checks. */
@Configuration(proxyBeanMethods = false)
public class KeycloakClientConfiguration {
    @Bean("keycloakRestClient")
    RestClient keycloakRestClient(KeycloakProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
