package com.rafaga.backend.orders.domain.model;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.function.Supplier;

public class CurrentOrderTenantIdSupplier implements Supplier<String> {
    @Override
    public String get() {
        return TenantContext.getCurrentTenantId();
    }
}
