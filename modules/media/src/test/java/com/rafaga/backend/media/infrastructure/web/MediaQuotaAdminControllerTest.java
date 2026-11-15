package com.rafaga.backend.media.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaQuotaAdminControllerTest {

    private static final String TENANT_ID = "tenant-admin-target";

    @Mock
    private TenantRegistry tenantRegistry;
    @Mock
    private MediaService mediaService;
    @Mock
    private TenantConfig tenant;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getsQuotaInsideTheRequestedTenantContext() {
        TenantMediaQuota quota = quota();
        when(tenantRegistry.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(mediaService.getQuotaStats()).thenAnswer(ignored -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_ID);
            return quota;
        });
        MediaQuotaAdminController controller = new MediaQuotaAdminController(tenantRegistry, mediaService);

        MediaQuotaAdminController.QuotaResponse response = controller.getQuota(TENANT_ID);

        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.effectiveUsedBytes()).isEqualTo(7_000_000L);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void restoresAnExistingTenantContextAfterUpdatingQuota() {
        TenantContext.setCurrentTenantId("previous-tenant");
        TenantMediaQuota quota = quota();
        when(tenantRegistry.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(mediaService.updateQuotaLimits(100L, 20_000_000L)).thenAnswer(ignored -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo(TENANT_ID);
            return quota;
        });
        MediaQuotaAdminController controller = new MediaQuotaAdminController(tenantRegistry, mediaService);

        controller.updateQuota(TENANT_ID,
                new MediaQuotaAdminController.UpdateQuotaRequest(100L, 20_000_000L));

        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("previous-tenant");
    }

    private TenantMediaQuota quota() {
        return new TenantMediaQuota(TENANT_ID, 100, 20_000_000L,
                10, 5_000_000L, 2, 2_000_000L);
    }
}
