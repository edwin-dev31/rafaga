package com.rafaga.backend.tenant.application;

import java.util.List;
import java.util.Map;

/**
 * Read-only, redacted operations summary exposed by the tenant module for
 * super-admin platform and per-tenant operational views.
 */
public interface TenantOperationsQuery {
    PlatformRoutingSummary platformSummary();

    TenantRouteOperationsSummary tenantSummary(String tenantId);

    record PlatformRoutingSummary(
            long totalTargets,
            long healthyTargets,
            long unhealthyTargets,
            long drainingTargets,
            long saturatedTargets,
            Map<String, Long> domainCountsByStatus) {
    }

    record TenantRouteOperationsSummary(List<DomainLifecycleSummary> domains) {
    }

    record DomainLifecycleSummary(
            String hostname,
            String status,
            String targetId,
            String targetType,
            String targetStatus,
            String targetCapacity,
            boolean hasLastError) {
    }
}
