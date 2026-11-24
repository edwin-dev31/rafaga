package com.rafaga.backend.orders.infrastructure.web;

import com.rafaga.backend.orders.application.command.CreateOrder;
import com.rafaga.backend.orders.application.port.OrderSearchFilter;
import com.rafaga.backend.orders.application.service.OrderReportView;
import com.rafaga.backend.orders.application.service.OrderService;
import com.rafaga.backend.orders.application.service.OrderView;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import com.rafaga.backend.tenant.application.RequiresFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiresFeature("orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(@Valid @RequestBody CreateRequest request) {
        return orders.createOrder(new CreateOrder(request.customerId(), request.lines().stream()
                .map(line -> new CreateOrder.Line(line.productId(), line.variantId(), line.quantity()))
                .toList()));
    }

    @GetMapping("/report")
    @RequiresFeature("reports")
    public OrderReportView report(
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        return orders.getOrderReport(from, to);
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable("orderId") UUID orderId) {
        return orders.getOrderById(orderId);
    }

    @PutMapping("/{orderId}/status")
    public OrderView updateStatus(@PathVariable("orderId") UUID orderId,
            @Valid @RequestBody StatusRequest request) {
        return orders.updateOrderStatus(orderId, request.status());
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("orderId") UUID orderId) {
        orders.cancelOrder(orderId);
    }

    @GetMapping
    public Page<OrderView> list(
            @RequestParam(name = "status", required = false) OrderStatus status,
            @RequestParam(name = "customerId", required = false) UUID customerId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            Pageable pageable) {
        return orders.getOrders(new OrderSearchFilter(status, customerId, from, to), pageable);
    }

    public record CreateRequest(@NotNull UUID customerId,
            @NotEmpty List<@Valid LineRequest> lines) { }

    public record LineRequest(@NotNull UUID productId, UUID variantId,
            @NotNull @Min(1) Integer quantity) { }

    public record StatusRequest(@NotNull OrderStatus status) { }
}
