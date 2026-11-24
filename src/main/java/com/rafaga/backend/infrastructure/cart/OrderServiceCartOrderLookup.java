package com.rafaga.backend.infrastructure.cart;

import com.rafaga.backend.cart.application.port.CartOrderLookup;
import com.rafaga.backend.orders.application.service.OrderService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceCartOrderLookup implements CartOrderLookup {
    private final OrderService orders;

    public OrderServiceCartOrderLookup(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public Optional<OrderReference> findOrderByCartId(UUID cartId) {
        return orders.findOrderByCartId(cartId)
                .map(order -> new OrderReference(order.id(), order.publicToken()));
    }
}
