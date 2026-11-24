package com.rafaga.backend.catalog.application.port;

import com.rafaga.backend.catalog.domain.model.ProductStatus;
import java.util.UUID;

public record ProductSearchFilter(String query, ProductStatus status, UUID categoryId) {
    public static ProductSearchFilter empty() {
        return new ProductSearchFilter(null, null, null);
    }

    public boolean isEmpty() {
        return (query == null || query.isBlank()) && status == null && categoryId == null;
    }
}
