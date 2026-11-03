package com.rafaga.backend.common.application.tenant;

public final class TenantContextNotSetException extends IllegalStateException {
    public TenantContextNotSetException() {
        super("No tenant context is associated with the current execution");
    }
}
