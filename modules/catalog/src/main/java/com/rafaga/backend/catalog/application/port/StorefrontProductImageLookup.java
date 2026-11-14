package com.rafaga.backend.catalog.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supplies the public image summaries for a group of storefront products.
 *
 * <p>The catalog owns its storefront contract; media implements this port so
 * catalog does not depend on the media module.</p>
 */
@org.springframework.modulith.NamedInterface("api")
public interface StorefrontProductImageLookup {

    Map<UUID, List<Image>> findByProductIds(Collection<UUID> productIds);

    record Image(
            String url,
            String contentType,
            Integer width,
            Integer height,
            int position,
            boolean primary) {
    }
}
