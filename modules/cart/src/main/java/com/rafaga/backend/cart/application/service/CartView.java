package com.rafaga.backend.cart.application.service;

import com.rafaga.backend.cart.domain.model.Cart;
import com.rafaga.backend.cart.domain.model.CartStatus;
import com.rafaga.backend.cart.domain.model.OwnerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartView(UUID id, String tenantId, OwnerType ownerType, UUID customerId,
        CartStatus status, BigDecimal total, List<CartItemView> items,
        Instant createdAt, Instant updatedAt, Instant checkedOutAt, UUID orderId,
        String orderPublicToken) {

    public static CartView from(Cart cart) {
        return new CartView(
                cart.getId(),
                cart.getTenantId(),
                cart.getOwnerType(),
                cart.getCustomerId(),
                cart.getStatus(),
                cart.getTotal(),
                cart.getItems().stream().map(CartItemView::from).toList(),
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getCheckedOutAt(),
                cart.getOrderId(),
                cart.getOrderPublicToken());
    }
}
