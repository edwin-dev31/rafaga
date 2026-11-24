package com.rafaga.backend.orders.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.orders.application.port.OrderSearchFilter;
import com.rafaga.backend.orders.application.port.OrderStore;
import com.rafaga.backend.orders.application.service.OrderService;
import com.rafaga.backend.orders.application.service.OrderView;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderSearchControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void forwardsStatusCustomerIdAndDateRangeFiltersWithPagination() throws Exception {
        OrderService service = mock(OrderService.class);
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-23T23:59:59Z");
        OrderView view = new OrderView(
                orderId, customerId, OrderStatus.PENDING,
                BigDecimal.valueOf(120000), BigDecimal.valueOf(120000), List.of(), from, from, null, null);
        when(service.getOrders(any(OrderSearchFilter.class), any()))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mvc.perform(get("/api/orders")
                        .param("status", "PENDING")
                        .param("customerId", customerId.toString())
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        ArgumentCaptor<OrderSearchFilter> filterCaptor = ArgumentCaptor.forClass(OrderSearchFilter.class);
        verify(service).getOrders(filterCaptor.capture(), any());
        assertThat(filterCaptor.getValue().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(filterCaptor.getValue().customerId()).isEqualTo(customerId);
        assertThat(filterCaptor.getValue().from()).isEqualTo(from);
        assertThat(filterCaptor.getValue().to()).isEqualTo(to);
    }

    @Test
    void rejectsInvertedDateRangeInOrderService() {
        TenantContext.setCurrentTenantId("tenant-1");
        OrderService service = new OrderService(mock(OrderStore.class), null, null, null);
        Instant from = Instant.parse("2026-11-23T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");

        assertThatThrownBy(() -> service.getOrders(new OrderSearchFilter(null, null, from, to), PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }
}
