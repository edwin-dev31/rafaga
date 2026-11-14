package com.rafaga.backend.media.domain.model;

import java.util.Set;

public enum AssetStatus {
    PENDING,
    READY,
    PENDING_DELETE,
    DELETED,
    FAILED;

    private static final Set<Set<AssetStatus>> TRANSITIONS = Set.of(
        Set.of(PENDING, READY),
        Set.of(PENDING, FAILED),
        Set.of(PENDING, PENDING_DELETE),
        Set.of(READY, PENDING_DELETE),
        Set.of(PENDING_DELETE, DELETED),
        Set.of(PENDING_DELETE, READY),
        Set.of(FAILED, PENDING_DELETE)
    );

    public boolean canTransitionTo(AssetStatus next) {
        return TRANSITIONS.stream().anyMatch(s -> s.contains(this) && s.contains(next));
    }
}
