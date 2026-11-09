package com.rafaga.backend.common.application.event;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.application.tenant.TenantContextNotSetException;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * ApplicationEventMulticaster that propagates the TenantContext to event listeners.
 * <p>
 * When an event is published, the current tenant ID is captured from the ThreadLocal
 * and restored in each listener's execution context. This ensures that listeners
 * can access the tenant context even when running asynchronously.
 * <p>
 * If no tenant context is set when publishing, listeners are invoked without
 * a tenant context (same as default behavior), allowing non-tenant-aware events
 * to propagate normally.
 */
public class TenantAwareApplicationEventMulticaster extends SimpleApplicationEventMulticaster {

    @Nullable
    private String tenantIdAtPublish;

    @Override
    public void multicastEvent(ApplicationEvent event) {
        multicastEvent(event, null);
    }

    @Override
    public void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType) {
        // Capture the tenant context before dispatching to listeners.
        tenantIdAtPublish = captureTenantId();

        try {
            super.multicastEvent(event, eventType);
        } finally {
            tenantIdAtPublish = null;
        }
    }

    @Override
    protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
        // Restore the tenant context captured at publish time.
        String tenantId = tenantIdAtPublish;
        boolean wasSet = TenantContext.isSet();
        String previousTenantId = wasSet ? TenantContext.getCurrentTenantId() : null;

        try {
            if (tenantId != null) {
                TenantContext.setCurrentTenantId(tenantId);
            } else if (!wasSet) {
                // No tenant at publish time and none was set — leave it clear.
                TenantContext.clear();
            }
            // If tenantId is null but wasSet is true, keep the previous tenant.

            super.invokeListener(listener, event);
        } finally {
            // Restore the previous state.
            if (tenantId != null) {
                TenantContext.clear();
                if (wasSet && previousTenantId != null) {
                    TenantContext.setCurrentTenantId(previousTenantId);
                }
            }
            // If we didn't set a tenant, leave the ThreadLocal as we found it.
        }
    }

    @Nullable
    private String captureTenantId() {
        try {
            return TenantContext.getCurrentTenantId();
        } catch (TenantContextNotSetException e) {
            return null;
        }
    }
}
