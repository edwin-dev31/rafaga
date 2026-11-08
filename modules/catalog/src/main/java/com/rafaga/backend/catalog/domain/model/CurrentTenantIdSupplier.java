package com.rafaga.backend.catalog.domain.model;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.function.Supplier;

/** Supplies the tenant parameter for Hibernate's automatically enabled catalog filter. */
public final class CurrentTenantIdSupplier implements Supplier<String> {
    @Override
    public String get() {
        return TenantContext.getCurrentTenantId();
    }
}
