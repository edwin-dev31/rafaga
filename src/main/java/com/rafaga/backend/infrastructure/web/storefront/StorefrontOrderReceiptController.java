package com.rafaga.backend.infrastructure.web.storefront;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.orders.application.service.OrderService;
import com.rafaga.backend.orders.application.service.OrderView;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only order receipt endpoint for storefront customers (e.g. WhatsApp checkout links).
 * Access requires the unguessable 256-bit {@code publicToken} scoped to the host-resolved tenant
 * and returns only non-PII receipt details with {@code Cache-Control: private, no-store}.
 */
@RestController
@RequestMapping("/api/storefront/orders")
public class StorefrontOrderReceiptController {

    private static final CacheControl PRIVATE_NO_STORE = CacheControl.noStore().cachePrivate();

    private final OrderService orders;
    private final ProductService products;
    private final TenantRegistry tenants;

    public StorefrontOrderReceiptController(OrderService orders, ProductService products,
            TenantRegistry tenants) {
        this.orders = orders;
        this.products = products;
        this.tenants = tenants;
    }

    @GetMapping("/{publicToken}")
    public ResponseEntity<OrderReceiptView> getReceipt(@PathVariable("publicToken") String publicToken) {
        Optional<OrderView> found = orders.findOrderByPublicToken(publicToken);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        OrderView order = found.get();
        String tenantId = TenantContext.getCurrentTenantId();
        String storeName = tenants.findById(tenantId)
                .map(TenantConfig::name)
                .orElse(tenantId);
        List<ReceiptLineView> items = order.lines().stream()
                .map(this::toReceiptLine)
                .toList();
        OrderReceiptView receipt = new OrderReceiptView(
                storeName,
                order.id(),
                formatReference(order.id()),
                order.statusName(),
                order.subtotal(),
                order.total(),
                items,
                order.createdAt(),
                order.confirmedAt(),
                order.completedAt());
        return ResponseEntity.ok()
                .cacheControl(PRIVATE_NO_STORE)
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(receipt);
    }

    private ReceiptLineView toReceiptLine(OrderView.LineView line) {
        String productName = line.productName();
        String sku = line.sku();
        String variantName = line.variantName();
        if (productName == null) {
            try {
                ProductView product = products.getProductById(line.productId());
                productName = product.name();
                sku = product.sku();
                if (line.variantId() != null) {
                    ProductView.VariantView variant = product.variants().stream()
                            .filter(candidate -> line.variantId().equals(candidate.id()))
                            .findFirst()
                            .orElse(null);
                    if (variant != null) {
                        variantName = variant.name();
                        if (variant.sku() != null && !variant.sku().isBlank()) {
                            sku = variant.sku();
                        }
                    }
                }
            } catch (CatalogNotFoundException ignored) {
                // Legacy pre-snapshot order whose product was later removed; keep line pricing intact.
            }
        }
        return new ReceiptLineView(
                line.productId(),
                line.variantId(),
                productName,
                sku,
                variantName,
                line.quantity(),
                line.unitPrice(),
                line.lineTotal());
    }

    private static String formatReference(UUID orderId) {
        return "RFG-" + orderId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public record OrderReceiptView(
            String storeName,
            UUID orderId,
            String reference,
            String status,
            BigDecimal subtotal,
            BigDecimal total,
            List<ReceiptLineView> items,
            Instant createdAt,
            Instant confirmedAt,
            Instant completedAt) { }

    public record ReceiptLineView(
            UUID productId,
            UUID variantId,
            String productName,
            String sku,
            String variantName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) { }
}
