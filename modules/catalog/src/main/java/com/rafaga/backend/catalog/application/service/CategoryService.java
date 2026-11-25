package com.rafaga.backend.catalog.application.service;

import com.rafaga.backend.catalog.application.exception.CatalogConflictException;
import com.rafaga.backend.catalog.application.exception.CatalogNotFoundException;
import com.rafaga.backend.catalog.application.port.CatalogStore;
import com.rafaga.backend.catalog.domain.model.Category;
import com.rafaga.backend.common.application.tenant.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    static final String CATEGORY_TREE_CACHE_KEY =
            "T(com.rafaga.backend.common.application.tenant.TenantContext).getCurrentTenantId()";

    private final CatalogStore store;

    public CategoryService(CatalogStore store) {
        this.store = store;
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", key = CATEGORY_TREE_CACHE_KEY)
    public CategoryView createCategory(String name, String slug, UUID parentId, int displayOrder) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (store.findCategoryBySlug(slug, tenantId).isPresent()) {
            throw new CatalogConflictException("Category slug already exists for tenant");
        }
        Category category = new Category(tenantId, name, slug, parent(parentId, tenantId), displayOrder);
        return CategoryView.from(store.saveCategory(category));
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", key = CATEGORY_TREE_CACHE_KEY)
    public CategoryView updateCategory(UUID categoryId, String name, String slug,
            UUID parentId, int displayOrder) {
        String tenantId = TenantContext.getCurrentTenantId();
        Category category = category(categoryId, tenantId);
        store.findCategoryBySlug(slug, tenantId).ifPresent(existing -> {
            if (!existing.getId().equals(categoryId)) {
                throw new CatalogConflictException("Category slug already exists for tenant");
            }
        });
        category.update(name, slug, parent(parentId, tenantId), displayOrder);
        return CategoryView.from(store.saveCategory(category));
    }

    @Transactional(readOnly = true)
    public CategoryView getCategoryById(UUID categoryId) {
        return CategoryView.from(category(categoryId, TenantContext.getCurrentTenantId()));
    }

    @Transactional
    @CacheEvict(cacheNames = "categories", key = CATEGORY_TREE_CACHE_KEY)
    public void deleteCategory(UUID categoryId) {
        String tenantId = TenantContext.getCurrentTenantId();
        Category category = category(categoryId, tenantId);
        if (store.hasChildCategories(categoryId, tenantId)) {
            throw new CatalogConflictException("Category has child categories and cannot be deleted");
        }
        if (store.hasProductsInCategory(categoryId, tenantId)) {
            throw new CatalogConflictException("Category is assigned to products and cannot be deleted");
        }
        store.deleteCategory(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "categories", key = CATEGORY_TREE_CACHE_KEY)
    public List<CategoryNode> getCategoryTree() {
        List<Category> categories = store.findCategories(TenantContext.getCurrentTenantId());
        Map<UUID, List<Category>> byParent = new HashMap<>();
        for (Category category : categories) {
            UUID parentId = category.getParent() == null ? null : category.getParent().getId();
            byParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(category);
        }
        return byParent.getOrDefault(null, List.of()).stream()
                .map(category -> node(category, byParent))
                .toList();
    }

    private CategoryNode node(Category category, Map<UUID, List<Category>> byParent) {
        return new CategoryNode(category.getId(), category.getName(), category.getSlug(),
                category.getDisplayOrder(),
                byParent.getOrDefault(category.getId(), List.of()).stream()
                        .map(child -> node(child, byParent)).toList());
    }

    private Category parent(UUID parentId, String tenantId) {
        return parentId == null ? null : category(parentId, tenantId);
    }

    private Category category(UUID id, String tenantId) {
        return store.findCategory(id, tenantId)
                .orElseThrow(() -> new CatalogNotFoundException("Category", id));
    }
}
