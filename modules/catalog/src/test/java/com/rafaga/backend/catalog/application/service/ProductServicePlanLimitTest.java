package com.rafaga.backend.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.command.CreateProduct;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProductServicePlanLimitTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsCreatingProductWhenTenantReachesStarterMaxProducts() {
        TenantContext.setCurrentTenantId("tenant-1");
        CatalogStore store = mock(CatalogStore.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TenantLimitsQuery limitsQuery = mock(TenantLimitsQuery.class);

        when(store.findProductBySku("SKU-101", "tenant-1")).thenReturn(Optional.empty());
        when(store.countProducts("tenant-1")).thenReturn(100L);
        when(limitsQuery.findLimits("tenant-1"))
                .thenReturn(Optional.of(PlanLimitsDefaults.forPlan("tenant-1", "starter", NOW)));

        ProductService service = new ProductService(store, events, limitsQuery, Clock.fixed(NOW, ZoneOffset.UTC));
        CreateProduct command = new CreateProduct(
                "SKU-101", "Camisa", "Desc", BigDecimal.valueOf(99000), Set.of(), List.of());

        assertThatThrownBy(() -> service.createProduct(command))
                .isInstanceOf(TenantLimitExceededException.class)
                .hasMessageContaining("products");
        verify(store, never()).saveProduct(any());
    }
}
