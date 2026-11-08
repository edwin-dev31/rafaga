package com.rafaga.backend.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.command.CreateProduct;
import com.rafaga.backend.catalog.application.command.UpdateProduct;
import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.domain.event.ProductCreatedEvent;
import com.rafaga.backend.catalog.domain.event.ProductDeletedEvent;
import com.rafaga.backend.catalog.domain.event.ProductUpdatedEvent;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ProductServiceTest {
    private final CatalogStore store = mock(CatalogStore.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ProductService service = new ProductService(store, events);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void publishesLifecycleEventsWithTheCurrentTenant() {
        TenantContext.setCurrentTenantId("tenant-a");
        UUID productId = UUID.randomUUID();
        when(store.findProductBySku("SKU-1", "tenant-a")).thenReturn(Optional.empty());
        when(store.saveProduct(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", productId);
            return product;
        });

        ProductView created = service.createProduct(new CreateProduct(
                "SKU-1", "Coffee", null, BigDecimal.TEN, Set.of(), List.of()));
        assertThat(created.id()).isEqualTo(productId);
        verify(events).publishEvent(new ProductCreatedEvent(productId, "tenant-a", "SKU-1"));

        when(store.findProduct(productId, "tenant-a")).thenReturn(Optional.of(
                new Product("tenant-a", "SKU-1", "Coffee", null, BigDecimal.TEN,
                        java.time.Instant.parse("2026-01-01T00:00:00Z"))));
        service.updateProduct(productId, new UpdateProduct(
                "Coffee Premium", null, BigDecimal.valueOf(12), ProductStatus.ACTIVE, null));
        org.mockito.ArgumentCaptor<ProductUpdatedEvent> updated =
                org.mockito.ArgumentCaptor.forClass(ProductUpdatedEvent.class);
        verify(events).publishEvent(updated.capture());
        assertThat(updated.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(updated.getValue().changes()).containsEntry("name", "Coffee Premium");

        service.deleteProduct(productId);
        verify(events).publishEvent(new ProductDeletedEvent(productId, "tenant-a"));
    }

    @Test
    void rejectsDuplicateSkuWithinCurrentTenant() {
        TenantContext.setCurrentTenantId("tenant-a");
        when(store.findProductBySku("SKU-1", "tenant-a")).thenReturn(Optional.of(mock(Product.class)));

        assertThatThrownBy(() -> service.createProduct(new CreateProduct(
                "SKU-1", "Coffee", null, BigDecimal.ONE, Set.of(), List.of())))
                .isInstanceOf(CatalogConflictException.class);
    }
}
