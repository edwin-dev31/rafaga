package com.rafaga.backend.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.port.StockStore;
import com.rafaga.backend.inventory.domain.model.StockLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StockSummaryTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void productWithAvailableVariantsIsInStockEvenWhenBaseStockIsZero() {
        TenantContext.setCurrentTenantId("tenant-1");
        StockStore store = mock(StockStore.class);
        UUID productWithVariants = UUID.randomUUID();
        UUID simpleProduct = UUID.randomUUID();
        UUID variantM = UUID.randomUUID();
        UUID variantL = UUID.randomUUID();

        StockLevel baseZero = new StockLevel("tenant-1", productWithVariants, null, NOW);
        StockLevel vM = new StockLevel("tenant-1", productWithVariants, variantM, NOW);
        vM.adjust(5, NOW);
        vM.reserve(2, NOW); // available = 3
        StockLevel vL = new StockLevel("tenant-1", productWithVariants, variantL, NOW);
        vL.adjust(4, NOW); // available = 4

        StockLevel simpleBase = new StockLevel("tenant-1", simpleProduct, null, NOW);
        simpleBase.adjust(10, NOW); // available = 10

        when(store.findLevelsForProducts("tenant-1", List.of(productWithVariants, simpleProduct)))
                .thenReturn(List.of(baseZero, vM, vL, simpleBase));

        StockService service = new StockService(
                store, mock(ProductService.class), mock(ApplicationEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        List<ProductStockSummaryView> summaries = service.getStockSummary(List.of(productWithVariants, simpleProduct));

        assertThat(summaries).hasSize(2);

        ProductStockSummaryView variantSummary = summaries.get(0);
        assertThat(variantSummary.productId()).isEqualTo(productWithVariants);
        assertThat(variantSummary.baseAvailable()).isZero();
        assertThat(variantSummary.variantsAvailable()).isEqualTo(7);
        assertThat(variantSummary.totalSellable()).isEqualTo(7);
        assertThat(variantSummary.hasVariants()).isTrue();
        assertThat(variantSummary.inStock()).isTrue();
        assertThat(variantSummary.variants()).hasSize(2);

        ProductStockSummaryView simpleSummary = summaries.get(1);
        assertThat(simpleSummary.productId()).isEqualTo(simpleProduct);
        assertThat(simpleSummary.baseAvailable()).isEqualTo(10);
        assertThat(simpleSummary.totalSellable()).isEqualTo(10);
        assertThat(simpleSummary.hasVariants()).isFalse();
        assertThat(simpleSummary.inStock()).isTrue();
    }
}
