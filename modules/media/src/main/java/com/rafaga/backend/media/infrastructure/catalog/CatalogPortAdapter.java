package com.rafaga.backend.media.infrastructure.catalog;

import com.rafaga.backend.catalog.application.service.CatalogProductAccess;
import com.rafaga.backend.media.application.port.out.CatalogPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CatalogPortAdapter implements CatalogPort {

    private final CatalogProductAccess catalogProductAccess;

    public CatalogPortAdapter(CatalogProductAccess catalogProductAccess) {
        this.catalogProductAccess = catalogProductAccess;
    }

    @Override
    public boolean productExists(UUID productId, String tenantId) {
        return catalogProductAccess.productExists(productId, tenantId);
    }
}
