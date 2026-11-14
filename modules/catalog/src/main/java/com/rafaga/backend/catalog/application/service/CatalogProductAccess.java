package com.rafaga.backend.catalog.application.service;

import java.util.UUID;

/** Public API for verifying product existence from other modules. */
public interface CatalogProductAccess {
    boolean productExists(UUID productId, String tenantId);
}
