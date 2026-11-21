package com.rafaga.backend.tenant.domain.model;

import java.util.EnumSet;

/** Lifecycle state of a hostname before it is eligible for gateway routing. */
public enum DomainStatus {
    REQUESTED,
    OWNERSHIP_PENDING,
    CERTIFICATE_PENDING,
    TARGET_PENDING,
    ACTIVE,
    SUSPENDED,
    FAILED,
    REMOVING;

    public boolean canTransitionTo(DomainStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case REQUESTED -> EnumSet.of(OWNERSHIP_PENDING, FAILED, REMOVING).contains(target);
            case OWNERSHIP_PENDING -> EnumSet.of(CERTIFICATE_PENDING, FAILED, REMOVING).contains(target);
            case CERTIFICATE_PENDING -> EnumSet.of(TARGET_PENDING, FAILED, REMOVING).contains(target);
            case TARGET_PENDING -> EnumSet.of(ACTIVE, FAILED, REMOVING).contains(target);
            case ACTIVE -> EnumSet.of(SUSPENDED, REMOVING).contains(target);
            case SUSPENDED -> EnumSet.of(ACTIVE, REMOVING).contains(target);
            case FAILED -> EnumSet.of(REQUESTED, REMOVING).contains(target);
            case REMOVING -> false;
        };
    }
}
