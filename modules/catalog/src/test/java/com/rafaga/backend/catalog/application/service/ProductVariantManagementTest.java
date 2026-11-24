package com.rafaga.backend.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductVariant;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProductVariantManagementTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updatesVariantAttributesAndArchivesInsteadOfDeleting() throws Exception {
        TenantContext.setCurrentTenantId("tenant-1");
        CatalogStore store = mock(CatalogStore.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        Product product = new Product("tenant-1", "CAM-001", "Camisa Lino", "Desc", BigDecimal.valueOf(120000), NOW);
        setField(product, "id", productId);
        ProductVariant variant = new ProductVariant(
                product, "CAM-001-M", "Talla M", Map.of("talla", "M", "color", "Arena"), BigDecimal.ZERO);
        setField(variant, "id", variantId);
        product.addVariant(variant);

        when(store.findProduct(productId, "tenant-1")).thenReturn(Optional.of(product));
        when(store.saveProduct(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductService service = new ProductService(store, events, null, Clock.fixed(NOW, ZoneOffset.UTC));

        ProductView updated = service.updateVariant(
                productId, variantId, "CAM-001-M-NG", "Talla M Negro",
                Map.of("talla", "M", "color", "Negro"), BigDecimal.valueOf(10000), VariantStatus.ACTIVE);

        assertThat(updated.variants()).hasSize(1);
        assertThat(updated.variants().getFirst().sku()).isEqualTo("CAM-001-M-NG");
        assertThat(updated.variants().getFirst().attributes()).containsEntry("color", "Negro");
        assertThat(updated.variants().getFirst().priceAdjustment()).isEqualByComparingTo("10000");

        ProductView archived = service.archiveVariant(productId, variantId);
        assertThat(archived.variants()).hasSize(1);
        assertThat(archived.variants().getFirst().status()).isEqualTo(VariantStatus.INACTIVE);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
