package com.rafaga.backend.catalog.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.catalog.application.port.ProductSearchFilter;
import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductSearchControllerTest {

    @Test
    void forwardsQueryStatusAndCategoryIdFiltersWithPagination() throws Exception {
        ProductService service = mock(ProductService.class);
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-23T12:00:00Z");
        ProductView view = new ProductView(
                productId, "CLN-001", "Cojín lino natural", "Desc",
                BigDecimal.valueOf(64900), ProductStatus.ACTIVE, Set.of(categoryId), List.of(), now, now);
        when(service.searchProducts(any(ProductSearchFilter.class), any()))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new CatalogExceptionHandler())
                .build();

        mvc.perform(get("/api/products")
                        .param("q", "cojin")
                        .param("status", "ACTIVE")
                        .param("categoryId", categoryId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("CLN-001"));

        ArgumentCaptor<ProductSearchFilter> filterCaptor = ArgumentCaptor.forClass(ProductSearchFilter.class);
        verify(service).searchProducts(filterCaptor.capture(), any());
        assertThat(filterCaptor.getValue().query()).isEqualTo("cojin");
        assertThat(filterCaptor.getValue().status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(filterCaptor.getValue().categoryId()).isEqualTo(categoryId);
    }
}
