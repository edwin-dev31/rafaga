package com.rafaga.backend.orders.application.service;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.common.domain.event.OrderCancelledEvent;
import com.rafaga.backend.common.domain.event.OrderConfirmedEvent;
import com.rafaga.backend.inventory.application.service.ReservationView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.orders.application.command.CreateOrder;
import com.rafaga.backend.orders.application.exception.OrderConflictException;
import com.rafaga.backend.orders.application.exception.OrderNotFoundException;
import com.rafaga.backend.orders.application.port.OrderStore;
import com.rafaga.backend.orders.domain.event.OrderCompletedEvent;
import com.rafaga.backend.orders.domain.event.OrderCreatedEvent;
import com.rafaga.backend.orders.domain.exception.InvalidOrderTransitionException;
import com.rafaga.backend.orders.domain.model.Order;
import com.rafaga.backend.orders.domain.model.OrderLine;
import com.rafaga.backend.orders.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderStore store;
    private final ProductService products;
    private final StockService stock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public OrderService(OrderStore store, ProductService products, StockService stock,
            ApplicationEventPublisher events) {
        this(store, products, stock, events, Clock.systemUTC());
    }

    OrderService(OrderStore store, ProductService products, StockService stock,
            ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.products = products;
        this.stock = stock;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public OrderView createOrder(CreateOrder command) {
        String tenantId = TenantContext.getCurrentTenantId();
        Order order = new Order(tenantId, command.customerId(), clock.instant());
        for (CreateOrder.Line line : command.lines()) {
            ProductView product = products.getProductById(line.productId());
            order.addLine(line.productId(), line.variantId(), line.quantity(), unitPrice(product, line.variantId()));
        }
        for (OrderLine line : order.getLines()) {
            stock.reserveStock(line.getProductId(), line.getVariantId(), line.getQuantity(),
                    order.getId().toString(), null);
        }
        Order saved = store.save(order);
        events.publishEvent(new OrderCreatedEvent(saved.getId(), tenantId, saved.getCustomerId()));
        return OrderView.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderView getOrderById(UUID orderId) {
        return OrderView.from(order(orderId, TenantContext.getCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public Page<OrderView> getOrders(Pageable pageable) {
        return store.findAll(TenantContext.getCurrentTenantId(), pageable).map(OrderView::from);
    }

    @Transactional
    public OrderView updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        if (newStatus == OrderStatus.CANCELLED) return cancelOrder(orderId);
        String tenantId = TenantContext.getCurrentTenantId();
        Order order = order(orderId, tenantId);
        if (newStatus == OrderStatus.CONFIRMED) {
            requireReservedLines(order);
            order.confirm(clock.instant());
            Order saved = store.save(order);
            events.publishEvent(new OrderConfirmedEvent(orderId, tenantId, saved.getLines().stream()
                    .map(line -> new OrderConfirmedEvent.Line(line.getProductId(), line.getVariantId(),
                            line.getQuantity())).toList()));
            return OrderView.from(saved);
        }
        if (newStatus == OrderStatus.COMPLETED) {
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                throw new InvalidOrderTransitionException(
                        "Cannot change order from " + order.getStatus() + " to COMPLETED");
            }
            List<ReservationView> reservations = requireReservedLines(order);
            for (ReservationView reservation : reservations) stock.confirmReservation(reservation.id());
            order.complete(clock.instant());
            Order saved = store.save(order);
            events.publishEvent(new OrderCompletedEvent(orderId, tenantId));
            return OrderView.from(saved);
        }
        throw new IllegalArgumentException("Unsupported target order status: " + newStatus);
    }

    @Transactional
    public OrderView cancelOrder(UUID orderId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Order order = order(orderId, tenantId);
        order.cancel(clock.instant());
        for (ReservationView reservation : stock.getActiveReservations(orderId.toString())) {
            stock.releaseReservation(reservation.id());
        }
        Order saved = store.save(order);
        events.publishEvent(new OrderCancelledEvent(orderId, tenantId));
        return OrderView.from(saved);
    }

    private List<ReservationView> requireReservedLines(Order order) {
        List<ReservationView> reservations = stock.getActiveReservations(order.getId().toString());
        if (reservations.size() != order.getLines().size()) {
            throw new OrderConflictException("Order stock reservation is incomplete or expired");
        }
        for (OrderLine line : order.getLines()) {
            boolean present = reservations.stream().anyMatch(reservation ->
                    reservation.productId().equals(line.getProductId())
                            && Objects.equals(reservation.variantId(), line.getVariantId())
                            && reservation.quantity() == line.getQuantity());
            if (!present) throw new OrderConflictException("Order stock reservation does not match its lines");
        }
        return reservations;
    }

    private BigDecimal unitPrice(ProductView product, UUID variantId) {
        if (variantId == null) return product.basePrice();
        ProductView.VariantView variant = product.variants().stream()
                .filter(candidate -> variantId.equals(candidate.id()))
                .findFirst().orElseThrow(() -> new CatalogNotFoundException("Product variant", variantId));
        BigDecimal price = product.basePrice().add(variant.priceAdjustment());
        if (price.signum() < 0) throw new OrderConflictException("Product variant price is negative");
        return price;
    }

    private Order order(UUID id, String tenantId) {
        return store.find(id, tenantId).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
