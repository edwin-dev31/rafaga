package com.rafaga.backend.tenant.infrastructure.cloudflare;

import com.rafaga.backend.tenant.application.port.CloudflareHostnamePort;
import com.rafaga.backend.tenant.application.port.CloudflareProvisioningException;
import com.rafaga.backend.tenant.application.port.CustomHostnameStatus;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** Production Cloudflare for SaaS custom-hostname adapter with bounded timeouts and safe errors. */
@Component
@Primary
@EnableConfigurationProperties(CloudflareProperties.class)
@ConditionalOnProperty(name = "rafaga.cloudflare.enabled", havingValue = "true")
public class CloudflareCustomHostnameAdapter implements CloudflareHostnamePort {
    private final RestClient client;
    private final String zoneId;

    public CloudflareCustomHostnameAdapter(CloudflareProperties properties) {
        if (properties.getZoneId() == null || properties.getZoneId().isBlank()
                || properties.getApiToken() == null || properties.getApiToken().isBlank()) {
            throw new IllegalStateException("rafaga.cloudflare.zone-id and api-token are required when enabled");
        }
        this.zoneId = properties.getZoneId().trim();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public CustomHostnameStatus createOrGetCustomHostname(String hostname) {
        Map<String, Object> body = Map.of(
                "hostname", hostname,
                "ssl", Map.of(
                        "method", "txt",
                        "type", "dv",
                        "settings", Map.of("min_tls_version", "1.2")));
        try {
            JsonNode response = client.post()
                    .uri("/zones/{zoneId}/custom_hostnames", zoneId)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return toStatus(requireResult(response, "CLOUDFLARE_CREATE_FAILED"));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                return findByHostname(hostname)
                        .orElseThrow(() -> new CloudflareProvisioningException(
                                "CLOUDFLARE_CONFLICT_NOT_FOUND",
                                "Custom hostname conflict could not be resolved",
                                true));
            }
            throw translateHttpError("CLOUDFLARE_CREATE_HTTP_" + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new CloudflareProvisioningException("CLOUDFLARE_NETWORK_ERROR", "Cloudflare API unreachable", true, ex);
        }
    }

    @Override
    public Optional<CustomHostnameStatus> getCustomHostname(String externalHostnameId, String hostname) {
        if (externalHostnameId == null || externalHostnameId.isBlank()) {
            return findByHostname(hostname);
        }
        try {
            JsonNode response = client.get()
                    .uri("/zones/{zoneId}/custom_hostnames/{id}", zoneId, externalHostnameId)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.of(toStatus(requireResult(response, "CLOUDFLARE_GET_FAILED")));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw translateHttpError("CLOUDFLARE_GET_HTTP_" + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new CloudflareProvisioningException("CLOUDFLARE_NETWORK_ERROR", "Cloudflare API unreachable", true, ex);
        }
    }

    @Override
    public void deleteCustomHostname(String externalHostnameId) {
        if (externalHostnameId == null || externalHostnameId.isBlank()) {
            return;
        }
        try {
            client.delete()
                    .uri("/zones/{zoneId}/custom_hostnames/{id}", zoneId, externalHostnameId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return;
            }
            throw translateHttpError("CLOUDFLARE_DELETE_HTTP_" + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new CloudflareProvisioningException("CLOUDFLARE_NETWORK_ERROR", "Cloudflare API unreachable", true, ex);
        }
    }

    private Optional<CustomHostnameStatus> findByHostname(String hostname) {
        try {
            JsonNode response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/zones/{zoneId}/custom_hostnames")
                            .queryParam("hostname", hostname)
                            .build(zoneId))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode result = response == null ? null : response.path("result");
            if (result != null && result.isArray() && !result.isEmpty()) {
                return Optional.of(toStatus(result.get(0)));
            }
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            throw translateHttpError("CLOUDFLARE_LOOKUP_HTTP_" + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new CloudflareProvisioningException("CLOUDFLARE_NETWORK_ERROR", "Cloudflare API unreachable", true, ex);
        }
    }

    private static JsonNode requireResult(JsonNode response, String fallbackCode) {
        if (response == null || !response.path("success").asBoolean(false) || response.path("result").isMissingNode()) {
            throw new CloudflareProvisioningException(fallbackCode, "Cloudflare API returned an unsuccessful response", true);
        }
        return response.path("result");
    }

    private static CustomHostnameStatus toStatus(JsonNode result) {
        String id = result.path("id").asString("");
        String hostname = result.path("hostname").asString("");
        String status = result.path("status").asString("");
        String sslStatus = result.path("ssl").path("status").asString("");
        boolean ownershipVerified = "active".equalsIgnoreCase(status);
        boolean certificateReady = "active".equalsIgnoreCase(sslStatus);
        boolean failed = List.of("blocked", "deleted", "moved").contains(status.toLowerCase())
                || List.of("validation_timed_out", "deleted", "expired").contains(sslStatus.toLowerCase());
        String failureCode = failed ? ("CLOUDFLARE_STATUS_" + (sslStatus.isBlank() ? status : sslStatus).toUpperCase()) : null;
        String verificationName = result.path("ownership_verification").path("name").asString(null);
        String verificationValue = result.path("ownership_verification").path("value").asString(null);
        return new CustomHostnameStatus(
                id,
                hostname,
                ownershipVerified,
                certificateReady,
                failed,
                failureCode,
                verificationName,
                verificationValue);
    }

    private static CloudflareProvisioningException translateHttpError(String code, RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        boolean retryable = status.is5xxServerError() || status.value() == 429 || status.value() == 408;
        return new CloudflareProvisioningException(code, "Cloudflare API rejected request with HTTP " + status.value(), retryable);
    }
}
