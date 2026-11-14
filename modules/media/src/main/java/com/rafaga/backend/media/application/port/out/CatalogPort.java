package com.rafaga.backend.media.application.port.out;

import java.util.UUID;

public interface CatalogPort {
    boolean productExists(UUID productId, String tenantId);
}
