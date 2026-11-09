package com.rafaga.backend.common.application.event;

import com.rafaga.backend.common.application.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantAwareApplicationEventMulticaster verifying TenantContext propagation.
 * These tests use the multicaster directly without Spring Boot context for speed and isolation.
 */
class TenantAwareApplicationEventMulticasterTest {

    private TenantAwareApplicationEventMulticaster multicaster;
    private TestEventListener listener1;
    private TestEventListener listener2;

    @BeforeEach
    void setUp() {
        multicaster = new TenantAwareApplicationEventMulticaster();
        listener1 = new TestEventListener();
        listener2 = new TestEventListener();
        multicaster.addApplicationListener(listener1);
        multicaster.addApplicationListener(listener2);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void propagatesTenantContextToSynchronousListener() {
        // Set tenant context before publishing
        TenantContext.setCurrentTenantId("tenant-a");

        multicaster.multicastEvent(new StringApplicationEvent("test-event"));

        // Both listeners should have received the tenant ID
        assertThat(listener1.receivedTenantIds).contains("tenant-a");
        assertThat(listener2.receivedTenantIds).contains("tenant-a");
    }

    @Test
    void multipleListenersReceiveSameTenantContext() {
        TenantContext.setCurrentTenantId("tenant-b");

        multicaster.multicastEvent(new StringApplicationEvent("multi-listener-test"));

        // Both listeners should have received the same tenant
        assertThat(listener1.receivedTenantIds).contains("tenant-b");
        assertThat(listener2.receivedTenantIds).contains("tenant-b");
    }

    @Test
    void listenerFailureDoesNotAffectTenantContext() {
        TenantContext.setCurrentTenantId("tenant-c");

        // Configure listener1 to fail
        listener1.shouldFail = true;

        // Set up error handler to suppress the exception
        multicaster.setErrorHandler(t -> {
            // swallow errors for this test
        });

        // Publish - listener1 fails, listener2 should still run
        multicaster.multicastEvent(new StringApplicationEvent("failure-test"));

        // Tenant context should still be set correctly after failure
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-c");
        // Listener2 should have still received the event
        assertThat(listener2.receivedTenantIds).contains("tenant-c");
    }

    @Test
    void asyncListenerReceivesTenantContext() throws InterruptedException {
        TenantContext.setCurrentTenantId("tenant-d");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedTenant = new AtomicReference<>();

        // Register an async listener
        TestAsyncListener asyncListener = new TestAsyncListener(latch, capturedTenant);
        multicaster.addApplicationListener(asyncListener);

        // Publish event
        multicaster.multicastEvent(new StringApplicationEvent("async-test"));

        // Give async listener time to process
        boolean awaited = latch.await(2, TimeUnit.SECONDS);
        assertThat(awaited).isTrue();

        // The tenant should have been captured even in async execution
        assertThat(capturedTenant.get()).isEqualTo("tenant-d");
    }

    @Test
    void noTenantContextDoesNotBreakListeners() {
        TenantContext.clear();

        // Publishing without tenant context should not throw
        multicaster.multicastEvent(new StringApplicationEvent("no-tenant-test"));

        // Listeners should have received null tenant
        assertThat(listener1.receivedTenantIds).contains((String) null);
        assertThat(listener2.receivedTenantIds).contains((String) null);
    }

    /**
     * Test event for publishing strings.
     */
    static class StringApplicationEvent extends ApplicationEvent {
        private final String payload;

        StringApplicationEvent(String payload) {
            super(payload);
            this.payload = payload;
        }

        String getPayload() {
            return payload;
        }
    }

    /**
     * Test listener that captures tenant IDs.
     */
    static class TestEventListener implements ApplicationListener<StringApplicationEvent> {
        List<String> receivedTenantIds = new ArrayList<>();
        boolean shouldFail = false;

        @Override
        public void onApplicationEvent(StringApplicationEvent event) {
            if (shouldFail) {
                throw new RuntimeException("Intentional failure for testing");
            }
            try {
                receivedTenantIds.add(TenantContext.getCurrentTenantId());
            } catch (Exception e) {
                receivedTenantIds.add(null);
            }
        }
    }

    /**
     * Async test listener that runs in a separate thread.
     */
    static class TestAsyncListener implements ApplicationListener<StringApplicationEvent> {
        private final CountDownLatch latch;
        private final AtomicReference<String> capturedTenant;

        TestAsyncListener(CountDownLatch latch, AtomicReference<String> capturedTenant) {
            this.latch = latch;
            this.capturedTenant = capturedTenant;
        }

        @Override
        public void onApplicationEvent(StringApplicationEvent event) {
            try {
                capturedTenant.set(TenantContext.getCurrentTenantId());
            } catch (Exception e) {
                capturedTenant.set(null);
            }
            latch.countDown();
        }
    }
}
