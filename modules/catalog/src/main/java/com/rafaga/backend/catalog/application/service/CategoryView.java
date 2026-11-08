package com.rafaga.backend.catalog.application.service;

import com.rafaga.backend.catalog.domain.model.Category;
import java.util.UUID;

public record CategoryView(UUID id, String name, String slug, UUID parentId, int displayOrder) {
    public static CategoryView from(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getSlug(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getDisplayOrder());
    }
}
