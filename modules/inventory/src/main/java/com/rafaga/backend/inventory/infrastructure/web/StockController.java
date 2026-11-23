package com.rafaga.backend.inventory.infrastructure.web;

import com.rafaga.backend.inventory.application.service.MovementView;
import com.rafaga.backend.inventory.application.service.ProductStockSummaryView;
import com.rafaga.backend.inventory.application.service.StockLevelView;
import com.rafaga.backend.inventory.application.service.StockService;
import com.rafaga.backend.tenant.application.RequiresFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@RequiresFeature("inventory")
public class StockController {
    private final StockService stock;

    public StockController(StockService stock) { this.stock = stock; }

    @GetMapping("/summary")
    public List<ProductStockSummaryView> summary(
            @RequestParam(name = "productIds") List<UUID> productIds) {
        return stock.getStockSummary(productIds);
    }

    @GetMapping("/{productId}/summary")
    public ProductStockSummaryView productSummary(@PathVariable("productId") UUID productId) {
        return stock.getProductStockSummary(productId);
    }

    @GetMapping("/{productId}")
    public StockLevelView get(@PathVariable("productId") UUID productId,
            @RequestParam(name = "variantId", required = false) UUID variantId) {
        return stock.getStockLevel(productId, variantId);
    }

    @PostMapping("/{productId}/adjust")
    public StockLevelView adjust(@PathVariable("productId") UUID productId,
            @Valid @RequestBody AdjustRequest request) {
        return stock.adjustStock(productId, request.variantId(), request.delta(), request.notes());
    }

    @GetMapping("/{productId}/movements")
    public List<MovementView> movements(@PathVariable("productId") UUID productId,
            @RequestParam(name = "variantId", required = false) UUID variantId) {
        return stock.getMovements(productId, variantId);
    }

    public record AdjustRequest(UUID variantId, @NotNull Integer delta, String notes) { }
}
