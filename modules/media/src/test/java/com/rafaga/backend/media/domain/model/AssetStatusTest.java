package com.rafaga.backend.media.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetStatusTest {

    @Test
    void pending_canTransitionToReady() {
        assertThat(AssetStatus.PENDING.canTransitionTo(AssetStatus.READY)).isTrue();
    }

    @Test
    void pending_canTransitionToFailed() {
        assertThat(AssetStatus.PENDING.canTransitionTo(AssetStatus.FAILED)).isTrue();
    }

    @Test
    void pending_canTransitionToPendingDelete() {
        assertThat(AssetStatus.PENDING.canTransitionTo(AssetStatus.PENDING_DELETE)).isTrue();
    }

    @Test
    void ready_canTransitionToPendingDelete() {
        assertThat(AssetStatus.READY.canTransitionTo(AssetStatus.PENDING_DELETE)).isTrue();
    }

    @Test
    void ready_cannotTransitionToPending() {
        assertThat(AssetStatus.READY.canTransitionTo(AssetStatus.PENDING)).isFalse();
    }

    @Test
    void ready_cannotTransitionToFailed() {
        assertThat(AssetStatus.READY.canTransitionTo(AssetStatus.FAILED)).isFalse();
    }

    @Test
    void pendingDelete_canTransitionToDeleted() {
        assertThat(AssetStatus.PENDING_DELETE.canTransitionTo(AssetStatus.DELETED)).isTrue();
    }

    @Test
    void pendingDelete_canTransitionToReady() {
        assertThat(AssetStatus.PENDING_DELETE.canTransitionTo(AssetStatus.READY)).isTrue();
    }

    @Test
    void pendingDelete_cannotTransitionToPending() {
        assertThat(AssetStatus.PENDING_DELETE.canTransitionTo(AssetStatus.PENDING)).isFalse();
    }

    @Test
    void deleted_cannotTransitionToAnyState() {
        assertThat(AssetStatus.DELETED.canTransitionTo(AssetStatus.READY)).isFalse();
        assertThat(AssetStatus.DELETED.canTransitionTo(AssetStatus.PENDING)).isFalse();
        assertThat(AssetStatus.DELETED.canTransitionTo(AssetStatus.PENDING_DELETE)).isFalse();
        assertThat(AssetStatus.DELETED.canTransitionTo(AssetStatus.FAILED)).isFalse();
    }

    @Test
    void failed_canTransitionToPendingDelete() {
        assertThat(AssetStatus.FAILED.canTransitionTo(AssetStatus.PENDING_DELETE)).isTrue();
    }

    @Test
    void failed_cannotTransitionToReady() {
        assertThat(AssetStatus.FAILED.canTransitionTo(AssetStatus.READY)).isFalse();
    }

    @Test
    void failed_cannotTransitionToPending() {
        assertThat(AssetStatus.FAILED.canTransitionTo(AssetStatus.PENDING)).isFalse();
    }

    @Test
    void anyStatus_cannotTransitionToSelf() {
        assertThat(AssetStatus.PENDING.canTransitionTo(AssetStatus.PENDING)).isFalse();
        assertThat(AssetStatus.READY.canTransitionTo(AssetStatus.READY)).isFalse();
        assertThat(AssetStatus.DELETED.canTransitionTo(AssetStatus.DELETED)).isFalse();
    }
}
