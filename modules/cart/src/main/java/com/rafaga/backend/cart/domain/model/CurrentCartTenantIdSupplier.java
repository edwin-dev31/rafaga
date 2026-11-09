package com.rafaga.backend.cart.domain.model;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.function.Supplier;

public class CurrentCartTenantIdSupplier implements Supplier<String> {
    @Override
    public String get() {
        return TenantContext.getCurrentTenantId();
    }
}
