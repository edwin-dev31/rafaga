package com.rafaga.backend.inventory.domain.model;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.function.Supplier;

public class CurrentInventoryTenantIdSupplier implements Supplier<String> {
    @Override
    public String get() {
        return TenantContext.getCurrentTenantId();
    }
}
