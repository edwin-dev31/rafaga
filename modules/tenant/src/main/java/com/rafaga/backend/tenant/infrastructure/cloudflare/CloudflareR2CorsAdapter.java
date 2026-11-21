package com.rafaga.backend.tenant.infrastructure.cloudflare;

import com.rafaga.backend.tenant.application.port.CloudflareProvisioningException;
import com.rafaga.backend.tenant.application.port.R2CorsPort;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Production Cloudflare R2 bucket CORS adapter using explicit HTTPS origins only. */
@Component
@ConditionalOnProperty(name = "rafaga.cloudflare.enabled", havingValue = "true")
public class CloudflareR2CorsAdapter implements R2CorsPort {
    private final RestClient client;
    private final String accountId;
    private final String bucket;

    public CloudflareR2CorsAdapter(CloudflareProperties properties) {
        if (properties.getAccountId() == null || properties.getAccountId().isBlank()
                || properties.getR2Bucket() == null || properties.getR2Bucket().isBlank()
                || properties.getApiToken() == null || properties.getApiToken().isBlank()) {
            throw new IllegalStateException("rafaga.cloudflare.account-id, r2-bucket, and api-token are required when enabled");
        }
        this.accountId = properties.getAccountId().trim();
        this.bucket = properties.getR2Bucket().trim();
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
    public void applyAllowedOrigins(List<String> explicitHttpsOrigins) {
        Map<String, Object> rule = Map.of(
                "allowed", Map.of(
                        "origins", explicitHttpsOrigins,
                        "methods", List.of("PUT"),
                        "headers", List.of("Content-Type")),
                "exposeHeaders", List.of("ETag"),
                "maxAgeSeconds", 300);
        Map<String, Object> body = Map.of("rules", List.of(rule));
        try {
            client.put()
                    .uri("/accounts/{accountId}/r2/buckets/{bucket}/cors", accountId, bucket)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            boolean retryable = status.is5xxServerError() || status.value() == 429 || status.value() == 408;
            throw new CloudflareProvisioningException(
                    "R2_CORS_HTTP_" + status.value(),
                    "Cloudflare R2 CORS update failed with HTTP " + status.value(),
                    retryable);
        } catch (RestClientException ex) {
            throw new CloudflareProvisioningException("R2_CORS_NETWORK_ERROR", "Cloudflare R2 CORS API unreachable", true, ex);
        }
    }
}
