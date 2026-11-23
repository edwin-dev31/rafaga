package com.rafaga.backend.tenant.application;

import java.util.Optional;

public interface TenantLimitsQuery {
    TenantLimits getLimits(String tenantId);

    Optional<TenantLimits> findLimits(String tenantId);
}
