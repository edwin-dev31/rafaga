package com.rafaga.backend.tenant.infrastructure.target;

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
                .build();
    }

    @Override
    public boolean isHealthy(String protocol, String privateOrigin, String healthPath) {
        if (privateOrigin == null || privateOrigin.isBlank() || healthPath == null || healthPath.isBlank()) {
            return false;
        }
        String base = privateOrigin.trim().replaceAll("/+$", "");
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
}
