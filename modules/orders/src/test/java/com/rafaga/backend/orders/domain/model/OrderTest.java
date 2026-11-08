package com.rafaga.backend.orders.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rafaga.backend.orders.domain.exception.InvalidOrderTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void calculatesLineAndOrderTotals() {
        Order order = new Order("tenant-a", UUID.randomUUID(), NOW);
        UUID product = UUID.randomUUID();

        order.addLine(product, null, 3, new BigDecimal("12.50"));
        order.addLine(UUID.randomUUID(), null, 2, new BigDecimal("4.25"));

        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getLines().getFirst().getLineTotal()).isEqualByComparingTo("37.50");
        assertThat(order.getSubtotal()).isEqualByComparingTo("46.00");
        assertThat(order.getTotal()).isEqualByComparingTo("46.00");
        assertThatThrownBy(() -> order.addLine(product, null, 1, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesConfirmationCompletionAndCancellationTransitions() {
        Order completed = new Order("tenant-a", UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> completed.complete(NOW))
                .isInstanceOf(InvalidOrderTransitionException.class);
        completed.confirm(NOW.plusSeconds(1));
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        completed.complete(NOW.plusSeconds(2));
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> completed.cancel(NOW.plusSeconds(3)))
                .isInstanceOf(InvalidOrderTransitionException.class);

        Order cancelled = new Order("tenant-a", UUID.randomUUID(), NOW);
        cancelled.cancel(NOW.plusSeconds(1));
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.confirm(NOW.plusSeconds(2)))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }
}
