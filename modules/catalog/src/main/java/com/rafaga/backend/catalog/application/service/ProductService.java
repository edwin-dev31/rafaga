package com.rafaga.backend.catalog.application.service;

import com.rafaga.backend.catalog.application.command.CreateProduct;
import com.rafaga.backend.catalog.application.command.UpdateProduct;
import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.application.port.ProductSearchFilter;
import com.rafaga.backend.catalog.domain.event.ProductCreatedEvent;
import com.rafaga.backend.catalog.domain.event.ProductDeletedEvent;
import com.rafaga.backend.catalog.domain.event.ProductUpdatedEvent;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductVariant;
import com.rafaga.backend.catalog.domain.model.VariantStatus;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService implements CatalogProductAccess {
    static final String PRODUCT_CACHE_KEY =
            "T(com.rafaga.backend.common.application.tenant.TenantContext).getCurrentTenantId() + ':' + #p0";

    private final CatalogStore store;
    private final ApplicationEventPublisher events;
    private final TenantLimitsQuery limitsQuery;
    private final Clock clock;

    public ProductService(CatalogStore store, ApplicationEventPublisher events) {
        this(store, events, null, Clock.systemUTC());
    }

    ProductService(CatalogStore store, ApplicationEventPublisher events, Clock clock) {
        this(store, events, null, clock);
    }

    @Autowired
    public ProductService(
            CatalogStore store,
            ApplicationEventPublisher events,
            ObjectProvider<TenantLimitsQuery> limitsQueryProvider) {
        this(store, events, limitsQueryProvider.getIfAvailable(), Clock.systemUTC());
    }

    ProductService(CatalogStore store, ApplicationEventPublisher events, TenantLimitsQuery limitsQuery, Clock clock) {
        this.store = store;
        this.events = events;
        this.limitsQuery = limitsQuery;
        this.clock = clock;
    }

    @Transactional
    public ProductView createProduct(CreateProduct command) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (store.findProductBySku(command.sku(), tenantId).isPresent()) {
            throw new CatalogConflictException("Product SKU already exists for tenant");
        }
        if (limitsQuery != null) {
            limitsQuery.findLimits(tenantId).ifPresent(limits -> {
                long currentProducts = store.countProducts(tenantId);
                if (currentProducts >= limits.maxProducts()) {
                    throw new TenantLimitExceededException("products", limits.maxProducts(), currentProducts);
                }
            });
        }
        Product product = new Product(tenantId, command.sku(), command.name(),
                command.description(), command.basePrice(), clock.instant());
        for (Category category : categories(command.categoryIds(), tenantId)) {
            product.addCategory(category);
        }
        for (CreateProduct.Variant variant : command.variants()) {
            product.addVariant(new ProductVariant(product, variant.sku(), variant.name(),
                    variant.attributes(), variant.priceAdjustment()));
        }
        Product saved = store.saveProduct(product);
        events.publishEvent(new ProductCreatedEvent(saved.getId(), tenantId, saved.getSku()));
        return ProductView.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", key = PRODUCT_CACHE_KEY)
    public ProductView updateProduct(UUID productId, UpdateProduct command) {
        String tenantId = TenantContext.getCurrentTenantId();
        Product product = product(productId, tenantId);
        product.update(command.name(), command.description(), command.basePrice(),
                command.status(), clock.instant());
        if (command.categoryIds() != null) {
            product.replaceCategories(categories(command.categoryIds(), tenantId));
        }
        Product saved = store.saveProduct(product);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("name", saved.getName());
        changes.put("description", saved.getDescription());
        changes.put("basePrice", saved.getBasePrice());
        changes.put("status", saved.getStatus());
        if (command.categoryIds() != null) {
            changes.put("categoryIds", command.categoryIds());
        }
        events.publishEvent(new ProductUpdatedEvent(productId, tenantId, changes));
        return ProductView.from(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "products", key = PRODUCT_CACHE_KEY)
    public ProductView getProductById(UUID productId) {
        return ProductView.from(product(productId, TenantContext.getCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> searchProducts(Pageable pageable) {
        return searchProducts(ProductSearchFilter.empty(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductView> searchProducts(ProductSearchFilter filter, Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (filter == null || filter.isEmpty()) {
            return store.searchProducts(tenantId, pageable).map(ProductView::from);
        }
        return store.searchProducts(tenantId, filter, pageable).map(ProductView::from);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", key = PRODUCT_CACHE_KEY)
    public void deleteProduct(UUID productId) {
        String tenantId = TenantContext.getCurrentTenantId();
        store.deleteProduct(product(productId, tenantId));
        events.publishEvent(new ProductDeletedEvent(productId, tenantId));
    }

    @Transactional
    @CacheEvict(cacheNames = "products", key = PRODUCT_CACHE_KEY)
    public ProductView addVariant(UUID productId, CreateProduct.Variant command) {
        String tenantId = TenantContext.getCurrentTenantId();
        Product product = product(productId, tenantId);
        product.addVariant(new ProductVariant(
                product, command.sku(), command.name(), command.attributes(), command.priceAdjustment()));
        Product saved = store.saveProduct(product);
        events.publishEvent(new ProductUpdatedEvent(productId, tenantId, Map.of("variantAdded", command.sku())));
        return ProductView.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", key = PRODUCT_CACHE_KEY)
    public ProductView updateVariant(
            UUID productId,
            UUID variantId,
            String sku,
            String name,
            Map<String, String> attributes,
            java.math.BigDecimal priceAdjustment,
            VariantStatus status) {
        String tenantId = TenantContext.getCurrentTenantId();
        Product product = product(productId, tenantId);
        ProductVariant variant = product.getVariants().stream()
                .filter(candidate -> variantId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new CatalogNotFoundException("Product variant", variantId));
        variant.update(sku, name, attributes, priceAdjustment, status);
        Product saved = store.saveProduct(product);
        events.publishEvent(new ProductUpdatedEvent(productId, tenantId, Map.of("variantUpdated", variantId.toString())));
        return ProductView.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", key = PRODUCT_CACHE_KEY)
    public ProductView archiveVariant(UUID productId, UUID variantId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Product product = product(productId, tenantId);
        ProductVariant variant = product.getVariants().stream()
                .filter(candidate -> variantId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new CatalogNotFoundException("Product variant", variantId));
        variant.archive();
        Product saved = store.saveProduct(product);
        events.publishEvent(new ProductUpdatedEvent(productId, tenantId, Map.of("variantArchived", variantId.toString())));
        return ProductView.from(saved);
    }

    private Product product(UUID id, String tenantId) {
        return store.findProduct(id, tenantId)
                .orElseThrow(() -> new CatalogNotFoundException("Product", id));
    }

    private Set<Category> categories(Set<UUID> categoryIds, String tenantId) {
        Set<Category> result = new LinkedHashSet<>();
        for (UUID id : categoryIds) {
            result.add(store.findCategory(id, tenantId)
                    .orElseThrow(() -> new CatalogNotFoundException("Category", id)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean productExists(UUID productId, String tenantId) {
        return store.findProduct(productId, tenantId).isPresent();
    }

    @Transactional(readOnly = true)
    public long countProducts() {
        return store.countProducts(TenantContext.getCurrentTenantId());
    }
}
