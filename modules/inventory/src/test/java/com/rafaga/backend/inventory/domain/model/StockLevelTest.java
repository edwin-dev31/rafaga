package com.rafaga.backend.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rafaga.backend.inventory.domain.exception.InsufficientStockException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockLevelTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void reservationReleaseAndConfirmationMaintainQuantities() {
        StockLevel level = new StockLevel("tenant-a", UUID.randomUUID(), null, now);
        level.adjust(10, now);
        assertThat(level.canReserve(7)).isTrue();
        level.reserve(7, now);
        assertThat(level.getTotalQuantity()).isEqualTo(10);
        assertThat(level.getAvailableQuantity()).isEqualTo(3);
        assertThat(level.getReservedQuantity()).isEqualTo(7);
        level.release(2, now);
        level.confirm(5, now);
        assertThat(level.getTotalQuantity()).isEqualTo(5);
        assertThat(level.getAvailableQuantity()).isEqualTo(5);
        assertThat(level.getReservedQuantity()).isZero();
    }

    @Test
    void rejectsInsufficientStockAndNegativeAvailableAdjustment() {
        StockLevel level = new StockLevel("tenant-a", UUID.randomUUID(), null, now);
        level.adjust(3, now);
        assertThatThrownBy(() -> level.reserve(4, now)).isInstanceOf(InsufficientStockException.class);
        level.reserve(2, now);
        assertThatThrownBy(() -> level.adjust(-2, now)).isInstanceOf(InsufficientStockException.class);
        assertThat(level.getAvailableQuantity()).isEqualTo(1);
    }
}
