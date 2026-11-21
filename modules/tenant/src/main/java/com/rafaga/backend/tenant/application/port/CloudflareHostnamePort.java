package com.rafaga.backend.tenant.application.port;

import java.util.Optional;

/** Outbound port for Cloudflare for SaaS custom hostname lifecycle operations. */
public interface CloudflareHostnamePort {
    CustomHostnameStatus createOrGetCustomHostname(String hostname);

    Optional<CustomHostnameStatus> getCustomHostname(String externalHostnameId, String hostname);

    void deleteCustomHostname(String externalHostnameId);
}
