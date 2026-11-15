package com.rafaga.backend.media.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.dto.MediaAdminSummary;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.AssetStatus;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaAdminControllerTest {

    @Mock private TenantRegistry tenantRegistry;
    @Mock private MediaService mediaService;
    @Mock private TenantConfig alpha;
    @Mock private TenantConfig beta;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void overviewPaginatesTenantsAndRunsEachSummaryInsideItsTenantContext() {
        when(alpha.tenantId()).thenReturn("alpha");
        when(alpha.name()).thenReturn("Alpha");
        when(beta.tenantId()).thenReturn("beta");
        when(tenantRegistry.findAll()).thenReturn(List.of(beta, alpha));
        when(mediaService.getAdminSummary()).thenAnswer(invocation -> summary(TenantContext.getCurrentTenantId()));
        MediaAdminController controller = new MediaAdminController(tenantRegistry, mediaService);

        MediaAdminController.OverviewResponse response = controller.overview(0, 1);

        assertThat(response.content()).singleElement().satisfies(tenant -> {
            assertThat(tenant.tenantId()).isEqualTo("alpha");
            assertThat(tenant.tenantName()).isEqualTo("Alpha");
        });
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void tenantSummaryRestoresThePreviousTenantContext() {
        TenantContext.setCurrentTenantId("caller-tenant");
        when(alpha.tenantId()).thenReturn("alpha");
        when(alpha.name()).thenReturn("Alpha");
        when(tenantRegistry.findById("alpha")).thenReturn(Optional.of(alpha));
        when(mediaService.getAdminSummary()).thenAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenantId()).isEqualTo("alpha");
            return summary("alpha");
        });
        MediaAdminController controller = new MediaAdminController(tenantRegistry, mediaService);

        MediaAdminController.TenantSummaryResponse response = controller.tenantSummary("alpha");

        assertThat(response.assetsByStatus()).containsEntry(AssetStatus.READY, 1L);
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("caller-tenant");
    }

    private MediaAdminSummary summary(String tenantId) {
        TenantMediaQuota quota = new TenantMediaQuota(tenantId, 100, 1_000_000L,
                1, 100L, 0, 0L);
        return new MediaAdminSummary(quota,
                Map.of(AssetStatus.READY, 1L, AssetStatus.PENDING, 0L, AssetStatus.PENDING_DELETE, 0L,
                        AssetStatus.FAILED, 0L, AssetStatus.DELETED, 0L),
                List.of(), List.of());
    }
}
