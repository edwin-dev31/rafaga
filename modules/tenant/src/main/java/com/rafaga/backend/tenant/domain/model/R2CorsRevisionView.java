package com.rafaga.backend.tenant.domain.model;

import java.time.Instant;
import java.util.List;

/** Public view of an applied R2 CORS revision for audit and rollback. */
@org.springframework.modulith.NamedInterface("api")
public record R2CorsRevisionView(
        long version,
        List<String> allowedOrigins,
        Instant appliedAt,
        Long rollbackOfVersion,
        String status) {
}
