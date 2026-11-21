package com.rafaga.backend.tenant.application.port;

import java.util.List;

/** Outbound port for applying explicit, non-wildcard CORS origins to the Cloudflare R2 bucket. */
public interface R2CorsPort {
    void applyAllowedOrigins(List<String> explicitHttpsOrigins);
}
