package com.rafaga.backend.tenant.application;

/** Safe, aggregate metrics from the shared database pool. */
public interface PlatformDataSourceMetrics {
    PoolMetrics sharedPoolMetrics();

    record PoolMetrics(int active, int idle, int total, int maximum, int pending) { }
}
