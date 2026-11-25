package com.rafaga.backend.infrastructure.web.storefront;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.inventory.application.service.ProductStockSummaryView;
import com.rafaga.backend.inventory.application.service.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StorefrontAvailabilityControllerTest {

    @Test
    void returnsZeroAvailabilityForVariantsWithoutStockRowUsingSingleSummaryQuery() throws Exception {
        ProductService productService = mock(ProductService.class);
        StockService stockService = mock(StockService.class);
        UUID productId = UUID.randomUUID();
        UUID stockedVariantId = UUID.randomUUID();
        UUID uninitializedVariantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-25T12:00:00Z");

        when(productService.getProductById(productId)).thenReturn(new ProductView(
                productId, "CAM-1", "Camiseta", null, BigDecimal.TEN, ProductStatus.ACTIVE, Set.of(),
                List.of(
                        new ProductView.VariantView(
                                stockedVariantId, "CAM-1-M", "M", Map.of(), BigDecimal.ZERO, VariantStatus.ACTIVE),
                        new ProductView.VariantView(
                                uninitializedVariantId, "CAM-1-L", "L", Map.of(), BigDecimal.ZERO, VariantStatus.ACTIVE)),
                now, now));
        when(stockService.getProductStockSummary(productId)).thenReturn(new ProductStockSummaryView(
                productId, 0, 5, 5, true, true,
                List.of(new ProductStockSummaryView.VariantStockView(stockedVariantId, 5, 0, 5, true))));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new StorefrontAvailabilityController(productService, stockService)).build();

        mvc.perform(get("/api/storefront/products/{productId}/availability", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.variants[0].variantId").value(stockedVariantId.toString()))
                .andExpect(jsonPath("$.variants[0].availableQuantity").value(5))
                .andExpect(jsonPath("$.variants[0].inStock").value(true))
                .andExpect(jsonPath("$.variants[1].variantId").value(uninitializedVariantId.toString()))
                .andExpect(jsonPath("$.variants[1].availableQuantity").value(0))
                .andExpect(jsonPath("$.variants[1].inStock").value(false));

        verify(stockService).getProductStockSummary(productId);
    }

    @Test
    void returnsNotFoundApiErrorWhenProductDoesNotExist() throws Exception {
        ProductService productService = mock(ProductService.class);
        StockService stockService = mock(StockService.class);
        UUID missingId = UUID.randomUUID();
        when(productService.getProductById(missingId)).thenThrow(new CatalogNotFoundException("Product", missingId));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new StorefrontAvailabilityController(productService, stockService)).build();

        mvc.perform(get("/api/storefront/products/{productId}/availability", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
