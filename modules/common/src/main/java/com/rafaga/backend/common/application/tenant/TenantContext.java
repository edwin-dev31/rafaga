package com.rafaga.backend.common.application.tenant;

import java.util.Objects;

/** Holds the tenant identifier for the current execution thread. */
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(Objects.requireNonNull(tenantId, "tenantId must not be null"));
    }

    public static String getCurrentTenantId() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantContextNotSetException();
        }
        return tenantId;
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
