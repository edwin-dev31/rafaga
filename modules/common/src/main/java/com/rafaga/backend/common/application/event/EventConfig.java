package com.rafaga.backend.common.application.event;

import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Spring ApplicationEventMulticaster to propagate TenantContext
 * to all event listeners, ensuring tenant isolation across event-driven communication.
 * <p>
 * Event listeners receive the same tenant ID that was active when the event was published,
 * even when running asynchronously. This is essential for multi-tenant correctness:
 * a listener processing a CartCheckedOutEvent must operate on the same tenant that
 * published the event.
 * <p>
 * Best practices for event listeners:
 * <ul>
 *   <li>Use {@code @TransactionalEventListener(phase = AFTER_COMMIT)} for listeners
 *   that require the publisher's transaction to commit first.</li>
 *   <li>Use {@code @Async} with {@code @TransactionalEventListener} for fire-and-forget
 *   listeners that should not block the publisher.</li>
 *   <li>Listeners execute in a separate transaction from the publisher by default
 *   when using {@code @TransactionalEventListener}.</li>
 * </ul>
 */
@Configuration
public class EventConfig {

    @Bean
    public ApplicationEventMulticaster applicationEventMulticaster() {
        return new TenantAwareApplicationEventMulticaster();
    }
}
