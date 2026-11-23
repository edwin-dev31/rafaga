package com.rafaga.backend.tenant.application;

public class TenantLimitExceededException extends RuntimeException {
    private final String resource;
    private final long limit;
    private final long current;

    public TenantLimitExceededException(String resource, long limit, long current) {
        super("Tenant plan limit exceeded for " + resource + ": current=" + current + ", max=" + limit);
        this.resource = resource;
        this.limit = limit;
        this.current = current;
    }

    public String getResource() {
        return resource;
    }

    public long getLimit() {
        return limit;
    }

    public long getCurrent() {
        return current;
    }
}
