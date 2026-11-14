package com.rafaga.backend.media.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMediaQuotaTest {

    @Test
    void newQuota_allowsReservation() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 500, 5_368_709_120L, 0, 0L, 0, 0L
        );

        assertThat(quota.canReserve(5_242_880L)).isTrue();
    }

    @Test
    void canReserve_checksEffectiveAssets() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 500, 5_368_709_120L, 499, 0L, 1, 0L
        );

        // effectiveUsedAssets = 499 + 1 = 500, which equals maxAssets
        assertThat(quota.canReserve(1000L)).isFalse();
    }

    @Test
    void canReserve_checksEffectiveBytes() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 500, 10_000L, 10, 5_000L, 0, 4_000L
        );

        // effectiveUsedBytes = 5000 + 4000 = 9000
        // 9000 + 1000 = 10000 <= 10000 → true
        assertThat(quota.canReserve(1000L)).isTrue();
        // 9000 + 1001 = 10001 > 10000 → false
        assertThat(quota.canReserve(1001L)).isFalse();
    }

    @Test
    void effectiveUsedAssets_includesReserved() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 500, 5_368_709_120L, 10, 0L, 5, 0L
        );

        assertThat(quota.effectiveUsedAssets()).isEqualTo(15);
    }

    @Test
    void effectiveUsedBytes_includesReserved() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 500, 5_368_709_120L, 10, 5_000_000L, 5, 1_000_000L
        );

        assertThat(quota.effectiveUsedBytes()).isEqualTo(6_000_000L);
    }

    @Test
    void isStorageExceeded_checksEffectiveBytes() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 500, 10_000L, 10, 6_000L, 0, 5_001L
        );

        assertThat(quota.isStorageExceeded()).isTrue();
    }

    @Test
    void isCountExceeded_checksEffectiveAssets() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 10, 5_368_709_120L, 8, 0L, 3, 0L
        );

        assertThat(quota.isCountExceeded()).isTrue();
    }

    @Test
    void canReserve_falseWhenBothExceeded() {
        TenantMediaQuota quota = new TenantMediaQuota(
            "tenant-1", 10, 10_000L, 10, 10_000L, 0, 0L
        );

        assertThat(quota.canReserve(1L)).isFalse();
    }
}
