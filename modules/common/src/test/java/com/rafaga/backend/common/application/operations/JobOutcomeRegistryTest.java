package com.rafaga.backend.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobOutcomeRegistryTest {
    @Test
    void keepsOnlyTheLatestSafeOutcomeForEachJob() {
        JobOutcomeRegistry registry = new JobOutcomeRegistry();

        registry.execute("inventory.reservation-expiry", () -> { });
        assertThatThrownBy(() -> registry.execute("inventory.reservation-expiry", () -> {
            throw new IllegalStateException("database details must not be exposed");
        })).isInstanceOf(IllegalStateException.class);
        registry.execute("media.expired-pending-cleanup", () -> { });

        assertThat(registry.snapshot()).extracting(JobOutcomeRegistry.Outcome::job)
                .containsExactly("inventory.reservation-expiry", "media.expired-pending-cleanup");
        assertThat(registry.snapshot().getFirst().status()).isEqualTo(JobOutcomeRegistry.Status.FAILURE);
    }
}
