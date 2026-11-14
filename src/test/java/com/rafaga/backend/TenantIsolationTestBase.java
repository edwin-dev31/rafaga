package com.rafaga.backend;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.rafaga.backend.tenant.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/** Shared PostgreSQL-backed fixture for tests that prove tenant data isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class TenantIsolationTestBase {
    protected static final String TENANT_A = "isolation-a";
    protected static final String TENANT_B = "isolation-b";
    protected static final String HOST_A = "a.isolation.test";
    protected static final String HOST_B = "b.isolation.test";

    @Autowired
    protected TenantRegistry tenants;

    @BeforeEach
    void registerIsolationTenants() {
        tenants.register(isolationTenant(TENANT_A, HOST_A));
        tenants.register(isolationTenant(TENANT_B, HOST_B));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    protected final void withinTenant(String tenantId, Runnable action) {
        TenantContext.setCurrentTenantId(tenantId);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }

    protected TenantConfig isolationTenant(String tenantId, String host) {
        return new TenantConfig(tenantId, tenantId, List.of(host), DeploymentMode.SHARED_DB,
                null, "test", TenantStatus.ACTIVE, Map.of("test", true));
    }
}
