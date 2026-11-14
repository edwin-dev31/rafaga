package com.rafaga.backend.catalog.application.service.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.application.port.StorefrontProductImageLookup;
import com.rafaga.backend.catalog.application.service.CategoryService;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StorefrontCatalogServiceTest {

    private static final String TENANT_ID = "storefront-test";

    @Mock
    private CatalogStore store;
    @Mock
    private CategoryService categoryService;
    @Mock
    private StorefrontProductImageLookup productImageLookup;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void listProducts_includesEveryPublicImageForEachProduct() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        Product product = activeProduct();
        StorefrontProductImageLookup.Image primary = new StorefrontProductImageLookup.Image(
                "https://media.example/primary.webp", "image/webp", 800, 600, 0, true);
        StorefrontProductImageLookup.Image secondary = new StorefrontProductImageLookup.Image(
                "https://media.example/secondary.webp", "image/webp", 640, 480, 1, false);
        StorefrontCatalogService service = new StorefrontCatalogService(
                store, categoryService, productImageLookup);

        when(store.findActiveProducts(TENANT_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(productImageLookup.findByProductIds(List.of(product.getId())))
                .thenReturn(Map.of(product.getId(), List.of(primary, secondary)));

        StorefrontProductResponse response = service.listProducts(PageRequest.of(0, 20))
                .getContent()
                .getFirst();

        assertThat(response.images()).hasSize(2);
        assertThat(response.images().getFirst().url())
                .isEqualTo("https://media.example/primary.webp");
        assertThat(response.images().get(1).primary()).isFalse();
    }

    private Product activeProduct() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Product product = new Product(TENANT_ID, "BOOK-1", "Public book", null,
                new BigDecimal("34.99"), now);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.update("Public book", null, new BigDecimal("34.99"), ProductStatus.ACTIVE, now);
        return product;
    }
}
