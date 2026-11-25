package com.rafaga.backend.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.catalog.application.command.CreateProduct;
import com.rafaga.backend.catalog.application.command.UpdateProduct;
import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import com.rafaga.backend.catalog.domain.model.ProductVariant;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CatalogCacheInvalidationTest.TestCacheConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class CatalogCacheInvalidationTest {
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CatalogStore store;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        clearInvocations(store);
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evictsCachedProductAcrossUpdateAndAllVariantMutationsWhileIsolatingOtherTenants() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID otherTenantProductId = productId;

        Product tenantAProduct = new Product(
                "tenant-a", "SKU-A", "Original Name", "Desc", BigDecimal.valueOf(100), NOW);
        setField(tenantAProduct, "id", productId);

        Product tenantBProduct = new Product(
                "tenant-b", "SKU-B", "Other Store Product", "Desc B", BigDecimal.valueOf(200), NOW);
        setField(tenantBProduct, "id", otherTenantProductId);

        when(store.findProduct(productId, "tenant-a")).thenReturn(Optional.of(tenantAProduct));
        when(store.findProduct(otherTenantProductId, "tenant-b")).thenReturn(Optional.of(tenantBProduct));
        when(store.saveProduct(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            for (ProductVariant variant : saved.getVariants()) {
                if (variant.getId() == null) {
                    setField(variant, "id", UUID.randomUUID());
                }
            }
            return saved;
        });

        // Warm cache for both tenants on the same productId
        TenantContext.setCurrentTenantId("tenant-b");
        assertThat(productService.getProductById(otherTenantProductId).name()).isEqualTo("Other Store Product");
        assertThat(productService.getProductById(otherTenantProductId).name()).isEqualTo("Other Store Product");
        verify(store, times(1)).findProduct(otherTenantProductId, "tenant-b");

        TenantContext.setCurrentTenantId("tenant-a");
        ProductView cachedInitial = productService.getProductById(productId);
        assertThat(cachedInitial.name()).isEqualTo("Original Name");
        assertThat(cachedInitial.variants()).isEmpty();
        assertThat(productService.getProductById(productId).name()).isEqualTo("Original Name");
        verify(store, times(1)).findProduct(productId, "tenant-a");

        // 1. updateProduct must evict tenant-a:productId so getProductById returns updated fields
        productService.updateProduct(
                productId,
                new UpdateProduct("Updated Name", "Updated Desc", BigDecimal.valueOf(125), ProductStatus.ACTIVE, null));
        ProductView afterUpdate = productService.getProductById(productId);
        assertThat(afterUpdate.name()).isEqualTo("Updated Name");
        assertThat(afterUpdate.basePrice()).isEqualByComparingTo("125");

        // 2. addVariant must evict tenant-a:productId so inventory/cart callers see the new variant immediately
        ProductView variantAdded = productService.addVariant(
                productId,
                new CreateProduct.Variant("SKU-A-M", "Medium", Map.of("size", "M"), BigDecimal.TEN));
        UUID variantId = variantAdded.variants().getFirst().id();

        ProductView afterAddVariant = productService.getProductById(productId);
        assertThat(afterAddVariant.variants()).hasSize(1);
        assertThat(afterAddVariant.variants().getFirst().id()).isEqualTo(variantId);
        assertThat(afterAddVariant.variants().getFirst().sku()).isEqualTo("SKU-A-M");

        // 3. updateVariant must evict tenant-a:productId
        productService.updateVariant(
                productId,
                variantId,
                "SKU-A-L",
                "Large",
                Map.of("size", "L"),
                BigDecimal.valueOf(15),
                VariantStatus.ACTIVE);
        ProductView afterUpdateVariant = productService.getProductById(productId);
        assertThat(afterUpdateVariant.variants().getFirst().sku()).isEqualTo("SKU-A-L");
        assertThat(afterUpdateVariant.variants().getFirst().name()).isEqualTo("Large");

        // 4. archiveVariant must evict tenant-a:productId
        productService.archiveVariant(productId, variantId);
        ProductView afterArchiveVariant = productService.getProductById(productId);
        assertThat(afterArchiveVariant.variants().getFirst().status()).isEqualTo(VariantStatus.INACTIVE);

        // Tenant B cache entry for the same UUID must still be cached (no cross-tenant eviction)
        clearInvocations(store);
        TenantContext.setCurrentTenantId("tenant-b");
        assertThat(productService.getProductById(otherTenantProductId).name()).isEqualTo("Other Store Product");
        verify(store, never()).findProduct(otherTenantProductId, "tenant-b");
    }

    @Test
    void categoryMutationsEvictOnlyCurrentTenantCategoryTree() throws Exception {
        UUID catA = UUID.randomUUID();
        UUID catB = UUID.randomUUID();
        Category categoryA = new Category("tenant-a", "Ropa", "ropa", null, 0);
        setField(categoryA, "id", catA);
        Category categoryB = new Category("tenant-b", "Calzado", "calzado", null, 0);
        setField(categoryB, "id", catB);

        when(store.findCategories("tenant-a")).thenReturn(List.of(categoryA));
        when(store.findCategories("tenant-b")).thenReturn(List.of(categoryB));
        when(store.findCategoryBySlug("nueva", "tenant-a")).thenReturn(Optional.empty());
        when(store.findCategory(catA, "tenant-a")).thenReturn(Optional.of(categoryA));
        when(store.findCategoryBySlug("ropa-actualizada", "tenant-a")).thenReturn(Optional.empty());
        when(store.saveCategory(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });

        // Warm category tree cache for both tenants
        TenantContext.setCurrentTenantId("tenant-a");
        assertThat(categoryService.getCategoryTree()).hasSize(1);
        assertThat(categoryService.getCategoryTree()).hasSize(1);
        verify(store, times(1)).findCategories("tenant-a");

        TenantContext.setCurrentTenantId("tenant-b");
        assertThat(categoryService.getCategoryTree()).hasSize(1);
        assertThat(categoryService.getCategoryTree()).hasSize(1);
        verify(store, times(1)).findCategories("tenant-b");

        clearInvocations(store);

        // Mutating tenant-a categories evicts tenant-a but leaves tenant-b cached
        TenantContext.setCurrentTenantId("tenant-a");
        categoryService.createCategory("Nueva", "nueva", null, 1);
        categoryService.getCategoryTree();
        verify(store, times(1)).findCategories("tenant-a");

        categoryService.updateCategory(catA, "Ropa Actualizada", "ropa-actualizada", null, 0);
        categoryService.getCategoryTree();
        verify(store, times(2)).findCategories("tenant-a");

        TenantContext.setCurrentTenantId("tenant-b");
        assertThat(categoryService.getCategoryTree()).hasSize(1);
        verify(store, never()).findCategories("tenant-b");
    }

    @Test
    void deleteCategoryValidatesDependenciesAndEvictsOnlyCurrentTenantCache() throws Exception {
        UUID catId = UUID.randomUUID();
        Category category = new Category("tenant-a", "Accesorios", "accesorios", null, 0);
        setField(category, "id", catId);
        when(store.findCategory(catId, "tenant-a")).thenReturn(Optional.of(category));
        when(store.findCategories("tenant-a")).thenReturn(List.of(category));

        TenantContext.setCurrentTenantId("tenant-a");
        categoryService.getCategoryTree();
        clearInvocations(store);

        when(store.hasChildCategories(catId, "tenant-a")).thenReturn(true);
        assertThatThrownBy(() -> categoryService.deleteCategory(catId))
                .isInstanceOf(CatalogConflictException.class);

        when(store.hasChildCategories(catId, "tenant-a")).thenReturn(false);
        when(store.hasProductsInCategory(catId, "tenant-a")).thenReturn(true);
        assertThatThrownBy(() -> categoryService.deleteCategory(catId))
                .isInstanceOf(CatalogConflictException.class);

        when(store.hasProductsInCategory(catId, "tenant-a")).thenReturn(false);
        categoryService.deleteCategory(catId);
        verify(store).deleteCategory(category);

        categoryService.getCategoryTree();
        verify(store, times(1)).findCategories("tenant-a");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    static class TestCacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("products", "categories");
        }

        @Bean
        CatalogStore catalogStore() {
            return mock(CatalogStore.class);
        }

        @Bean
        ApplicationEventPublisher eventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        ProductService productService(CatalogStore store, ApplicationEventPublisher events) {
            return new ProductService(store, events, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean
        CategoryService categoryService(CatalogStore store) {
            return new CategoryService(store);
        }
    }
}
