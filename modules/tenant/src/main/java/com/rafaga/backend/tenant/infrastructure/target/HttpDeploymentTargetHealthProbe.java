package com.rafaga.backend.tenant.infrastructure.target;

import com.rafaga.backend.common.application.CorrelationIdPropagation;
import com.rafaga.backend.tenant.application.port.DeploymentTargetHealthProbe;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Bounded-timeout HTTP health probe for dedicated backend deployment targets. */
@Component
public class HttpDeploymentTargetHealthProbe implements DeploymentTargetHealthProbe {
    private final RestClient client;

    public HttpDeploymentTargetHealthProbe() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
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

    @Override
    public boolean isHealthy(String protocol, String privateOrigin, String healthPath) {
        if (privateOrigin == null || privateOrigin.isBlank() || healthPath == null || healthPath.isBlank()) {
            return false;
        }
        String base = stripTrailingSlashes(privateOrigin.trim());
        String path = healthPath.startsWith("/") ? healthPath : "/" + healthPath;
        try {
            return client.get()
                    .uri(base + path)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception ex) {
            return false;
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
