package com.rafaga.backend.infrastructure.web.admin;

import com.rafaga.backend.common.application.CorrelationIdPropagation;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Queries the private gateway operations endpoint on demand with a short timeout.
 * Unavailable gateway state is always reported as UNKNOWN, never cached as healthy.
 */
@Component
public class GatewayOperationsClient {
    private final String operationsUrl;
    private final String operationsToken;
    private final RestClient client;

    public GatewayOperationsClient(
            @Value("${rafaga.gateway.operations-url:}") String operationsUrl,
            @Value("${rafaga.gateway.operations-token:${rafaga.tenant-directory.internal-token:}}") String operationsToken) {
        this.operationsUrl = operationsUrl == null ? "" : operationsUrl.trim();
        this.operationsToken = operationsToken == null ? "" : operationsToken.trim();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    CorrelationIdPropagation.applyIfPresent((k, v) -> {
                        if (request.getHeaders().getFirst(k) == null) {
                            request.getHeaders().set(k, v);
                        }
                    });
                    return execution.execute(request, body);
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    public GatewayStatusSummary fetchStatus() {
        if (operationsUrl.isBlank() || operationsToken.isBlank()) {
            return GatewayStatusSummary.unknown();
        }
        try {
            Map<String, Object> body = client.get()
                    .uri(operationsUrl)
                    .header("X-Gateway-Operations-Token", operationsToken)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return GatewayStatusSummary.unknown();
            }
            long version = asLong(body.get("directoryVersion"));
            boolean configValid = Boolean.TRUE.equals(body.get("configValid"));
            Long ageSeconds = body.get("configAgeSeconds") instanceof Number n ? n.longValue() : null;
            long activeHosts = asLong(body.get("activeHostsCount"));
            long rateLimitRejects = 0L;
            if (body.get("rateLimitRejects") instanceof Map<?, ?> rejects) {
                rateLimitRejects = asLong(rejects.get("count"));
            }
            return new GatewayStatusSummary(
                    configValid ? "UP" : "DEGRADED",
                    version,
                    configValid,
                    ageSeconds,
                    activeHosts,
                    rateLimitRejects);
        } catch (Exception ex) {
            return GatewayStatusSummary.unknown();
        }
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    public record GatewayStatusSummary(
            String status,
            long directoryVersion,
            boolean configValid,
            Long configAgeSeconds,
            long activeHostsCount,
            long rateLimitRejectsCount) {
        public static GatewayStatusSummary unknown() {
            return new GatewayStatusSummary("UNKNOWN", 0L, false, null, 0L, 0L);
        }
    }
}
