package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.service.TenantDirectoryService;
import com.rafaga.backend.tenant.domain.model.TenantDirectoryEntry;
import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.infrastructure.web.TenantDirectoryController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class TenantControlPlaneArchitectureTest {
    @Test
    void verifiesSpringModulithBoundariesAfterControlPlaneChanges() {
        ApplicationModules modules = ApplicationModules.of(BackendApplication.class);
        assertDoesNotThrow(() -> modules.verify());
    }

    @Test
    void directoryControllerRejectsMissingOrInvalidTokenAndReturnsSnapshotOnValidToken() {
        TenantDirectoryService service = mock(TenantDirectoryService.class);
        TenantDirectorySnapshot expected = new TenantDirectorySnapshot(
                2L,
                true,
                List.of(new TenantDirectoryEntry(
                        "shop.example.test",
                        "tenant-1",
                        "https://ui.example.test",
                        DeploymentTargetType.SHARED_POOL,
                        "shared-pool",
                        2L)),
                List.of("suspended.example.test"));
        when(service.snapshot(1L)).thenReturn(expected);

        TenantDirectoryController controller = new TenantDirectoryController(service, "secret-token-32-chars-minimum-01");

        assertThatThrownBy(() -> controller.snapshot(1L, null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> controller.snapshot(1L, "wrong-token"))
                .isInstanceOf(RuntimeException.class);

        TenantDirectorySnapshot actual = controller.snapshot(1L, "secret-token-32-chars-minimum-01");
        assertThat(actual).isEqualTo(expected);
        verify(service).snapshot(1L);
    }
}
