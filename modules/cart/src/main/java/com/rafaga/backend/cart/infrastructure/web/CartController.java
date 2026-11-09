package com.rafaga.backend.cart.infrastructure.web;

import com.rafaga.backend.cart.application.command.UpsertCartItem;
import com.rafaga.backend.cart.application.service.CartService;
import com.rafaga.backend.cart.application.service.CartView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
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
@RequestMapping("/api/carts")
public class CartController {

    private final CartService service;

    public CartController(CartService service) { this.service = service; }

    /** GET /api/carts?customerId=… — returns (or creates) the active cart for a customer. */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CartView getOrCreate(@RequestParam("customerId") @NotNull UUID customerId) {
        return service.getOrCreateCart(customerId);
    }

    /** GET /api/carts/{cartId} — fetch any cart by id. */
    @GetMapping("/{cartId}")
    public CartView get(@PathVariable("cartId") UUID cartId) {
        return service.getCart(cartId);
    }

    /** PUT /api/carts/items — add or update an item in the active cart. */
    @PutMapping("/items")
    public CartView upsertItem(@Valid @RequestBody UpsertItemRequest request) {
        return service.upsertItem(new UpsertCartItem(
                request.customerId(), request.productId(), request.variantId(), request.quantity()));
    }

    /** DELETE /api/carts/items/{itemId}?customerId=… — remove an item from the active cart. */
    @DeleteMapping("/items/{itemId}")
    public CartView removeItem(@PathVariable("itemId") UUID itemId,
            @RequestParam("customerId") @NotNull UUID customerId) {
        return service.removeItem(customerId, itemId);
    }

    /** POST /api/carts/checkout?customerId=… — check out the active cart. */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.OK)
    public CartView checkout(@RequestParam("customerId") @NotNull UUID customerId) {
        return service.checkout(customerId);
    }

    /** DELETE /api/carts?customerId=… — abandon the active cart. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@RequestParam("customerId") @NotNull UUID customerId) {
        service.abandon(customerId);
    }

    public record UpsertItemRequest(
            @NotNull UUID customerId,
            @NotNull UUID productId,
            UUID variantId,
            @NotNull @Min(1) Integer quantity) { }
}
