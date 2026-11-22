package com.rafaga.backend.infrastructure.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rafaga.backend.common.application.CorrelationIdPropagation;
import com.rafaga.backend.common.application.JobOutcomeRegistry;
import com.rafaga.backend.media.application.MediaOperations;
import com.rafaga.backend.tenant.application.PlatformDataSourceMetrics;
import com.rafaga.backend.tenant.application.TenantOperationsQuery;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * Verifies 7.2, 7.3, and 7.5:
 * - Health, platform overview, and per-tenant operations endpoints never leak credentials,
 *   JDBC URLs, secret references, exception messages, or internal origins.
 * - GatewayOperationsClient propagates X-Correlation-ID and degrades cleanly to UNKNOWN.
 */
class OperationsSecurityAndRedactionTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void operationsEndpointsNeverLeakCredentialsExceptionsOrSecretReferences() throws Exception {
        HealthIndicator shared = mock(HealthIndicator.class);
        when(shared.health()).thenReturn(Health.down(new RuntimeException("FATAL: password authentication failed for super-secret-pw"))
                .withDetail("jdbcUrl", "jdbc:postgresql://internal-pg:5432/rafaga?password=super-secret-pw")
                .build());

        TenantRegistry registry = mock(TenantRegistry.class);
        DatabaseConfig dbConfig = new DatabaseConfig("vip-db-secret-ref-999");
        TenantConfig vip = new TenantConfig(
                "tenant-vip",
                "VIP Store",
                List.of("vip.example.test"),
                DeploymentMode.DEDICATED_DB,
                dbConfig,
                "enterprise",
                TenantStatus.ACTIVE,
                Map.of());
        when(registry.findAll()).thenReturn(List.of(vip));
        when(registry.findById("tenant-vip")).thenReturn(Optional.of(vip));

        PlatformDataSourceMetrics poolMetrics = mock(PlatformDataSourceMetrics.class);
        when(poolMetrics.sharedPoolMetrics())
                .thenReturn(new PlatformDataSourceMetrics.PoolMetrics(1, 9, 10, 50, 0));

        MediaOperations mediaOps = mock(MediaOperations.class);
        when(mediaOps.quotaFor("tenant-vip"))
                .thenReturn(new MediaOperations.TenantQuota(1000, 10_000_000L, 12, 240_000L, 0, 0L));

        TenantOperationsQuery opsQuery = mock(TenantOperationsQuery.class);
        when(opsQuery.platformSummary()).thenReturn(new TenantOperationsQuery.PlatformRoutingSummary(
                2, 2, 0, 0, 0, Map.of("ACTIVE", 1L)));
        when(opsQuery.tenantSummary("tenant-vip")).thenReturn(new TenantOperationsQuery.TenantRouteOperationsSummary(
                List.of(new TenantOperationsQuery.DomainLifecycleSummary(
                        "vip.example.test", "ACTIVE", "shared-pool", "SHARED_POOL", "HEALTHY", "AVAILABLE", false))));

        SystemAdministrationController controller = new SystemAdministrationController(
                Map.of("sharedDatabaseHealthIndicator", shared),
                new ConcurrentMapCacheManager("tenants", "products"),
                registry,
                poolMetrics,
                mock(JobOutcomeRegistry.class),
                mediaOps,
                opsQuery,
                new GatewayOperationsClient("", ""));

        String healthJson = JSON.writeValueAsString(controller.health());
        String overviewJson = JSON.writeValueAsString(controller.overview());
        String tenantJson = JSON.writeValueAsString(controller.tenantOperations("tenant-vip"));

        for (String payload : List.of(healthJson, overviewJson, tenantJson)) {
            assertThat(payload)
                    .doesNotContain("super-secret-pw")
                    .doesNotContain("jdbc:postgresql")
                    .doesNotContain("vip-db-secret-ref-999")
                    .doesNotContain("password authentication failed")
                    .doesNotContain("RuntimeException");
        }

        assertThat(overviewJson).contains("\"totalTargets\":2").contains("\"status\":\"UNKNOWN\"");
        assertThat(tenantJson).contains("vip.example.test").contains("\"targetStatus\":\"HEALTHY\"");
    }

    @Test
    void gatewayOperationsClientPropagatesCorrelationIdAndReportsUnknownOnOutage() throws Exception {
        AtomicReference<String> seenCorrelationId = new AtomicReference<>();
        AtomicReference<String> seenToken = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/ops/status", exchange -> {
            seenCorrelationId.set(exchange.getRequestHeaders().getFirst(CorrelationIdPropagation.HEADER));
            seenToken.set(exchange.getRequestHeaders().getFirst("X-Gateway-Operations-Token"));
            byte[] body = ("{\"directoryVersion\":12,\"configValid\":true,\"configAgeSeconds\":4,"
                    + "\"activeHostsCount\":5,\"rateLimitRejects\":{\"windowSeconds\":300,\"count\":2}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        String corrId = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        MDC.put(CorrelationIdPropagation.MDC_KEY, corrId);
        GatewayOperationsClient client = new GatewayOperationsClient(
                "http://127.0.0.1:" + port + "/ops/status", "gateway-ops-token");

        GatewayOperationsClient.GatewayStatusSummary online = client.fetchStatus();
        assertThat(online.status()).isEqualTo("UP");
        assertThat(online.directoryVersion()).isEqualTo(12L);
        assertThat(online.configValid()).isTrue();
        assertThat(online.activeHostsCount()).isEqualTo(5L);
        assertThat(online.rateLimitRejectsCount()).isEqualTo(2L);
        assertThat(seenCorrelationId.get()).isEqualTo(corrId);
        assertThat(seenToken.get()).isEqualTo("gateway-ops-token");

        server.stop(0);

        GatewayOperationsClient.GatewayStatusSummary offline = client.fetchStatus();
        assertThat(offline.status()).isEqualTo("UNKNOWN");
        assertThat(offline.configValid()).isFalse();
    }
}
