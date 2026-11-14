package com.rafaga.backend.media.domain.model;

import java.util.Map;
import java.util.Set;

public enum AssetStatus {
    PENDING,
    READY,
    PENDING_DELETE,
    DELETED,
    FAILED;

    private static final Map<AssetStatus, Set<AssetStatus>> TRANSITIONS = Map.of(
        PENDING, Set.of(READY, FAILED, PENDING_DELETE),
        READY, Set.of(PENDING_DELETE),
        PENDING_DELETE, Set.of(DELETED, READY),
        FAILED, Set.of(PENDING_DELETE),
        DELETED, Set.of()
    );

    public boolean canTransitionTo(AssetStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
