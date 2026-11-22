package com.rafaga.backend.tenant.domain.model;

import java.util.EnumSet;

/** Controlled lifecycle of a tenant deployment-target transition. */
@org.springframework.modulith.NamedInterface("api")
public enum TenantTargetTransitionState {
    PREPARED,
    VALIDATED,
    ACTIVATED,
    DRAINED,
    ROLLED_BACK,
    FAILED;

    public boolean canTransitionTo(TenantTargetTransitionState next) {
        return switch (this) {
            case PREPARED -> EnumSet.of(VALIDATED, ROLLED_BACK, FAILED).contains(next);
            case VALIDATED -> EnumSet.of(ACTIVATED, ROLLED_BACK, FAILED).contains(next);
            case ACTIVATED -> EnumSet.of(DRAINED, ROLLED_BACK, FAILED).contains(next);
            case DRAINED, ROLLED_BACK, FAILED -> false;
        };
    }
}
