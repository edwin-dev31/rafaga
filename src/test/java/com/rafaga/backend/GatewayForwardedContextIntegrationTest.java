package com.rafaga.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the backend trust boundary behind Traefik:
 * - Untrusted callers cannot forge X-Forwarded-Host or X-Tenant-ID.
 * - Trusted Traefik proxy CIDRs preserve the validated store Host.
 */
@TestPropertySource(properties = {
        "rafaga.web.trusted-proxy.cidrs=172.31.240.0/24",
        "rafaga.tenant.internal-header.enabled=false"
})
class GatewayForwardedContextIntegrationTest extends TenantIsolationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void untrustedCallerCannotForgeTenantHostOrTenantHeader() throws Exception {
        mockMvc.perform(get("/api/storefront/products")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.25");
                            request.setServerName("unknown.external.test");
                            return request;
                        })
                        .header("X-Forwarded-Host", HOST_A)
                        .header("X-Tenant-ID", TENANT_A))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trustedTraefikProxyPreservesValidatedStoreHost() throws Exception {
        mockMvc.perform(get("/api/storefront/products")
                        .with(request -> {
                            request.setRemoteAddr("172.31.240.10");
                            request.setServerName(HOST_A);
                            return request;
                        })
                        .header("X-Forwarded-Host", HOST_A)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isOk());
    }
}
