package com.rafaga.backend.catalog.infrastructure.web;

import com.rafaga.backend.catalog.application.command.CreateProduct;
import com.rafaga.backend.catalog.application.command.UpdateProduct;
import com.rafaga.backend.catalog.application.port.ProductSearchFilter;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.tenant.application.RequiresFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RequestMapping("/api/products")
@RequiresFeature("catalog")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@Valid @RequestBody CreateRequest request) {
        List<CreateProduct.Variant> variants = request.variants() == null ? List.of()
                : request.variants().stream().map(VariantRequest::toCommand).toList();
        return products.createProduct(new CreateProduct(request.sku(), request.name(), request.description(),
                request.basePrice(), request.categoryIds() == null ? Set.of() : request.categoryIds(), variants));
    }

    @GetMapping("/{productId}")
    public ProductView get(@PathVariable("productId") UUID productId) {
        return products.getProductById(productId);
    }

    @PutMapping("/{productId}")
    public ProductView update(@PathVariable("productId") UUID productId, @Valid @RequestBody UpdateRequest request) {
        return products.updateProduct(productId, new UpdateProduct(request.name(), request.description(),
                request.basePrice(), request.status(), request.categoryIds()));
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("productId") UUID productId) {
        products.deleteProduct(productId);
    }

    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView addVariant(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody VariantRequest request) {
        return products.addVariant(productId, request.toCommand());
    }

    @PutMapping("/{productId}/variants/{variantId}")
    public ProductView updateVariant(
            @PathVariable("productId") UUID productId,
            @PathVariable("variantId") UUID variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        return products.updateVariant(
                productId,
                variantId,
                request.sku(),
                request.name(),
                request.attributes() == null ? Map.of() : request.attributes(),
                request.priceAdjustment(),
                request.status());
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    public ProductView archiveVariant(
            @PathVariable("productId") UUID productId,
            @PathVariable("variantId") UUID variantId) {
        return products.archiveVariant(productId, variantId);
    }

    @GetMapping
    public Page<ProductView> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "status", required = false) ProductStatus status,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            Pageable pageable) {
        return products.searchProducts(new ProductSearchFilter(query, status, categoryId), pageable);
    }

    public record CreateRequest(
            @NotBlank String sku,
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0.00") BigDecimal basePrice,
            Set<UUID> categoryIds,
            List<@Valid VariantRequest> variants) {
    }

    public record VariantRequest(
            @NotBlank String sku,
            @NotBlank String name,
            Map<String, String> attributes,
            @NotNull BigDecimal priceAdjustment) {
        CreateProduct.Variant toCommand() {
            return new CreateProduct.Variant(sku, name,
                    attributes == null ? Map.of() : attributes, priceAdjustment);
        }
    }

    public record UpdateRequest(
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0.00") BigDecimal basePrice,
            @NotNull ProductStatus status,
            Set<UUID> categoryIds) {
    }

    public record UpdateVariantRequest(
            @NotBlank String sku,
            @NotBlank String name,
            Map<String, String> attributes,
            @NotNull BigDecimal priceAdjustment,
            VariantStatus status) {
    }
}
