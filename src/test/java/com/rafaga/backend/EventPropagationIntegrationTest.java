package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.catalog.domain.event.ProductCreatedEvent;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.domain.event.OrderCancelledEvent;
import com.rafaga.backend.common.domain.event.OrderConfirmedEvent;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test that verifies TenantContext propagation across module boundaries
 * via the TenantAwareApplicationEventMulticaster.
 *
 * This test confirms that when an event is published from one module (e.g., catalog),
 * the listener in another module (e.g., inventory) receives the correct tenant context,
 * even when running asynchronously.
 */
@SpringBootTest
@ActiveProfiles("test")
class EventPropagationIntegrationTest {

    private static final String TENANT_A = "ep-a-" + UUID.randomUUID().toString().substring(0, 22);
    private static final String TENANT_B = "ep-b-" + UUID.randomUUID().toString().substring(0, 22);
    private static final String HOST_A = "ep-a-" + UUID.randomUUID().toString().substring(0, 8) + ".event.test";
    private static final String HOST_B = "ep-b-" + UUID.randomUUID().toString().substring(0, 8) + ".event.test";

    @Autowired private TenantRegistry tenants;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private TenantContextListener tenantContextListener;
    @Autowired private DomainEventTenantListener domainEventTenantListener;

    @BeforeEach
    void registerTenants() {
        tenants.register(tenant(TENANT_A, HOST_A));
        tenants.register(tenant(TENANT_B, HOST_B));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void tenantContextPropagatesToEventListener() throws InterruptedException {
        // Set tenant A context and publish an event
        TenantContext.setCurrentTenantId(TENANT_A);
        events.publishEvent(new TestModuleEvent("test-payload"));

        // Wait for the listener to capture the tenant
        boolean awaited = tenantContextListener.latch.await(2, TimeUnit.SECONDS);
        assertThat(awaited).isTrue();

        // The listener should have received tenant A's context
        assertThat(tenantContextListener.capturedTenant.get()).isEqualTo(TENANT_A);
    }

    @Test
    void tenantContextPropagatesThroughProductCreatedEvent() throws InterruptedException {
        // Reset listener state
        domainEventTenantListener.reset();

        // Publish ProductCreatedEvent within a transaction for tenant A
        UUID productId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(TENANT_A);
        transactions.executeWithoutResult(status ->
                events.publishEvent(new ProductCreatedEvent(productId, TENANT_A, "TEST-" + UUID.randomUUID())));

        // Wait for the listener to capture the tenant
        boolean awaited = domainEventTenantListener.latch.await(5, TimeUnit.SECONDS);
        assertThat(awaited).isTrue();

        // The listener should have received tenant A's context, not tenant B
        assertThat(domainEventTenantListener.capturedTenant.get()).isEqualTo(TENANT_A);
    }

    @Test
    void tenantContextPropagatesThroughOrderConfirmedEvent() throws InterruptedException {
        // Reset listener state
        domainEventTenantListener.reset();

        // Publish OrderConfirmedEvent within a transaction for tenant A
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(TENANT_A);
        transactions.executeWithoutResult(status ->
                events.publishEvent(new OrderConfirmedEvent(
                        orderId, TENANT_A, List.of(new OrderConfirmedEvent.Line(productId, null, 3)))));

        // Wait for the listener to capture the tenant
        boolean awaited = domainEventTenantListener.latch.await(5, TimeUnit.SECONDS);
        assertThat(awaited).isTrue();

        // The listener should have received tenant A's context
        assertThat(domainEventTenantListener.capturedTenant.get()).isEqualTo(TENANT_A);
    }

    @Test
    void tenantContextPropagatesThroughOrderCancelledEvent() throws InterruptedException {
        // Reset listener state
        domainEventTenantListener.reset();

        // Publish OrderCancelledEvent within a transaction for tenant B
        UUID orderId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(TENANT_B);
        transactions.executeWithoutResult(status ->
                events.publishEvent(new OrderCancelledEvent(orderId, TENANT_B)));

        // Wait for the listener to capture the tenant
        boolean awaited = domainEventTenantListener.latch.await(5, TimeUnit.SECONDS);
        assertThat(awaited).isTrue();

        // The listener should have received tenant B's context
        assertThat(domainEventTenantListener.capturedTenant.get()).isEqualTo(TENANT_B);
    }

    // A simple event to test TenantContext propagation
    static class TestModuleEvent {
        private final String payload;
        TestModuleEvent(String payload) { this.payload = payload; }
        String getPayload() { return payload; }
    }

    // Listener that captures the tenant ID from the event context (synchronous)
    static class TenantContextListener {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> capturedTenant = new AtomicReference<>();

        @EventListener(TestModuleEvent.class)
        public void onTestEvent(TestModuleEvent event) {
            try {
                capturedTenant.set(TenantContext.getCurrentTenantId());
            } catch (Exception e) {
                capturedTenant.set(null);
            }
            latch.countDown();
        }
    }

    // Listener that captures tenant from domain events (ProductCreated, OrderConfirmed, OrderCancelled)
    static class DomainEventTenantListener {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> capturedTenant = new AtomicReference<>();

        void reset() {
            latch.countDown(); // Release any waiting threads
            capturedTenant.set(null);
        }

        @EventListener
        public void onProductCreated(ProductCreatedEvent event) {
            try {
                capturedTenant.set(TenantContext.getCurrentTenantId());
            } catch (Exception e) {
                capturedTenant.set(null);
            }
            latch.countDown();
        }

        @EventListener
        public void onOrderConfirmed(OrderConfirmedEvent event) {
            try {
                capturedTenant.set(TenantContext.getCurrentTenantId());
            } catch (Exception e) {
                capturedTenant.set(null);
            }
            latch.countDown();
        }

        @EventListener
        public void onOrderCancelled(OrderCancelledEvent event) {
            try {
                capturedTenant.set(TenantContext.getCurrentTenantId());
            } catch (Exception e) {
                capturedTenant.set(null);
            }
            latch.countDown();
        }
    }

    @TestConfiguration
    static class TestListenerConfig {
        @Bean
        TenantContextListener tenantContextListener() {
            return new TenantContextListener();
        }

        @Bean
        DomainEventTenantListener domainEventTenantListener() {
            return new DomainEventTenantListener();
        }
    }

    private TenantConfig tenant(String id, String host) {
        return new TenantConfig(id, id, List.of(host), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of());
    }
}
