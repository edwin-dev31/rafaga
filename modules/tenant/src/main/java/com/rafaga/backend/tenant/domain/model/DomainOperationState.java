package com.rafaga.backend.tenant.domain.model;

/** Safe, non-secret operational status for a domain lifecycle request. */
public enum DomainOperationState {
    IDLE,
    PENDING,
    RUNNING,
    RETRY_SCHEDULED,
    SUCCEEDED,
    FAILED
}
