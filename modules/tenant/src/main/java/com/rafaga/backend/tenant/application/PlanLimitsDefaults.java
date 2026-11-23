package com.rafaga.backend.tenant.application;

import java.time.Instant;
import java.util.Locale;

/**
 * Default resource limits per commercial plan:
 * - starter (default): 100 products, 500 images, 1 GB storage, 1 admin user, 1 domain
 * - pro / professional: 500 products, 2000 images, 5 GB storage, 3 admin users, 2 domains
 * - enterprise: 1000 products, 4000 images, 20 GB storage, 5 admin users, 4 domains
 */
public final class PlanLimitsDefaults {
    public static final int STARTER_MAX_PRODUCTS = 100;
    public static final long STARTER_MAX_ASSETS = 500L;
    public static final long STARTER_MAX_STORAGE_BYTES = 1_073_741_824L;
    public static final int STARTER_MAX_ADMIN_USERS = 1;
    public static final int STARTER_MAX_DOMAINS = 1;

    public static final int PRO_MAX_PRODUCTS = 500;
    public static final long PRO_MAX_ASSETS = 2_000L;
    public static final long PRO_MAX_STORAGE_BYTES = 5_368_709_120L;
    public static final int PRO_MAX_ADMIN_USERS = 3;
    public static final int PRO_MAX_DOMAINS = 2;

    public static final int ENTERPRISE_MAX_PRODUCTS = PRO_MAX_PRODUCTS * 2;
    public static final long ENTERPRISE_MAX_ASSETS = PRO_MAX_ASSETS * 2;
    public static final long ENTERPRISE_MAX_STORAGE_BYTES = 21_474_836_480L;
    public static final int ENTERPRISE_MAX_ADMIN_USERS = 5;
    public static final int ENTERPRISE_MAX_DOMAINS = 4;

    private PlanLimitsDefaults() {
    }

    public static TenantLimits forPlan(String tenantId, String planId, Instant now) {
        String normalized = planId == null ? "starter" : planId.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pro", "professional" -> new TenantLimits(
                    tenantId,
                    planId,
                    PRO_MAX_PRODUCTS,
                    PRO_MAX_ASSETS,
                    PRO_MAX_STORAGE_BYTES,
                    PRO_MAX_ADMIN_USERS,
                    PRO_MAX_DOMAINS,
                    now);
            case "enterprise" -> new TenantLimits(
                    tenantId,
                    planId,
                    ENTERPRISE_MAX_PRODUCTS,
                    ENTERPRISE_MAX_ASSETS,
                    ENTERPRISE_MAX_STORAGE_BYTES,
                    ENTERPRISE_MAX_ADMIN_USERS,
                    ENTERPRISE_MAX_DOMAINS,
                    now);
            default -> new TenantLimits(
                    tenantId,
                    planId == null || planId.isBlank() ? "starter" : planId,
                    STARTER_MAX_PRODUCTS,
                    STARTER_MAX_ASSETS,
                    STARTER_MAX_STORAGE_BYTES,
                    STARTER_MAX_ADMIN_USERS,
                    STARTER_MAX_DOMAINS,
                    now);
        };
    }
}
