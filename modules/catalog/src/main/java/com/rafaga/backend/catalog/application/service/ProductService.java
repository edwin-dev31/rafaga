package com.rafaga.backend.catalog.application.service;

import com.rafaga.backend.catalog.application.command.CreateProduct;
import com.rafaga.backend.catalog.application.command.UpdateProduct;
import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.domain.event.ProductCreatedEvent;
import com.rafaga.backend.catalog.domain.event.ProductDeletedEvent;
import com.rafaga.backend.catalog.domain.event.ProductUpdatedEvent;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductVariant;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final CatalogStore store;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public ProductService(CatalogStore store, ApplicationEventPublisher events) {
        this(store, events, Clock.systemUTC());
    }

    ProductService(CatalogStore store, ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ProductView createProduct(CreateProduct command) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (store.findProductBySku(command.sku(), tenantId).isPresent()) {
            throw new CatalogConflictException("Product SKU already exists for tenant");
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
    @CacheEvict(cacheNames = "products", keyGenerator = "tenantScopedCacheKeyGenerator")
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
    @Cacheable(cacheNames = "products", keyGenerator = "tenantScopedCacheKeyGenerator")
    public ProductView getProductById(UUID productId) {
        return ProductView.from(product(productId, TenantContext.getCurrentTenantId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> searchProducts(Pageable pageable) {
        return store.searchProducts(TenantContext.getCurrentTenantId(), pageable).map(ProductView::from);
    }

    @Transactional
    @CacheEvict(cacheNames = "products", keyGenerator = "tenantScopedCacheKeyGenerator")
    public void deleteProduct(UUID productId) {
        String tenantId = TenantContext.getCurrentTenantId();
        store.deleteProduct(product(productId, tenantId));
        events.publishEvent(new ProductDeletedEvent(productId, tenantId));
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
}
