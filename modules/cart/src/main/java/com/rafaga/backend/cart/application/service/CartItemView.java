package com.rafaga.backend.cart.application.service;

import com.rafaga.backend.cart.domain.model.CartItem;
import java.math.BigDecimal;
import java.util.UUID;

public record CartItemView(UUID id, UUID productId, UUID variantId,
        int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    public static CartItemView from(CartItem item) {
        return new CartItemView(item.getId(), item.getProductId(), item.getVariantId(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}
