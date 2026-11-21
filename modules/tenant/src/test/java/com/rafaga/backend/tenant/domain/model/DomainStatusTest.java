package com.rafaga.backend.tenant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rafaga.backend.tenant.application.service.TenantDirectoryService;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainStatusTest {
    @Test
    void enforcesCompleteDomainLifecycleStateMachine() {
        assertThat(DomainStatus.REQUESTED.canTransitionTo(DomainStatus.OWNERSHIP_PENDING)).isTrue();
        assertThat(DomainStatus.REQUESTED.canTransitionTo(DomainStatus.FAILED)).isTrue();
        assertThat(DomainStatus.REQUESTED.canTransitionTo(DomainStatus.REMOVING)).isTrue();
        assertThat(DomainStatus.REQUESTED.canTransitionTo(DomainStatus.ACTIVE)).isFalse();

        assertThat(DomainStatus.OWNERSHIP_PENDING.canTransitionTo(DomainStatus.CERTIFICATE_PENDING)).isTrue();
        assertThat(DomainStatus.OWNERSHIP_PENDING.canTransitionTo(DomainStatus.ACTIVE)).isFalse();

        assertThat(DomainStatus.CERTIFICATE_PENDING.canTransitionTo(DomainStatus.TARGET_PENDING)).isTrue();
        assertThat(DomainStatus.CERTIFICATE_PENDING.canTransitionTo(DomainStatus.ACTIVE)).isFalse();

        assertThat(DomainStatus.TARGET_PENDING.canTransitionTo(DomainStatus.ACTIVE)).isTrue();
        assertThat(DomainStatus.TARGET_PENDING.canTransitionTo(DomainStatus.FAILED)).isTrue();

        assertThat(DomainStatus.ACTIVE.canTransitionTo(DomainStatus.SUSPENDED)).isTrue();
        assertThat(DomainStatus.ACTIVE.canTransitionTo(DomainStatus.REMOVING)).isTrue();
        assertThat(DomainStatus.ACTIVE.canTransitionTo(DomainStatus.CERTIFICATE_PENDING)).isFalse();

        assertThat(DomainStatus.SUSPENDED.canTransitionTo(DomainStatus.ACTIVE)).isTrue();
        assertThat(DomainStatus.SUSPENDED.canTransitionTo(DomainStatus.REMOVING)).isTrue();

        assertThat(DomainStatus.FAILED.canTransitionTo(DomainStatus.REQUESTED)).isTrue();
        assertThat(DomainStatus.FAILED.canTransitionTo(DomainStatus.REMOVING)).isTrue();
        assertThat(DomainStatus.FAILED.canTransitionTo(DomainStatus.ACTIVE)).isFalse();

        assertThat(DomainStatus.REMOVING.canTransitionTo(DomainStatus.ACTIVE)).isFalse();
        assertThat(DomainStatus.REMOVING.canTransitionTo(DomainStatus.REQUESTED)).isFalse();
    }

    @Test
    void onlyHealthyDeploymentTargetsAreRoutable() {
        assertThat(DeploymentTargetStatus.HEALTHY.isRoutable()).isTrue();
        assertThat(DeploymentTargetStatus.PREPARING.isRoutable()).isFalse();
        assertThat(DeploymentTargetStatus.UNHEALTHY.isRoutable()).isFalse();
        assertThat(DeploymentTargetStatus.DRAINING.isRoutable()).isFalse();
        assertThat(DeploymentTargetStatus.RETIRED.isRoutable()).isFalse();
    }

    @Test
    void recordsAuditTimestampsAndOperationStatesAcrossTransitions() {
        Instant t0 = Instant.parse("2026-11-21T10:00:00Z");
        Instant t1 = t0.plusSeconds(10);
        Instant t2 = t0.plusSeconds(20);
        Instant t3 = t0.plusSeconds(30);
        Instant t4 = t0.plusSeconds(40);
        Instant t5 = t0.plusSeconds(50);

        TenantDomainControl domain = new TenantDomainControl(
                "tenant-1", "shop.example.test", DomainStatus.REQUESTED, "https://ui.example.test", "shared-pool", t0);

        domain.transition(DomainStatus.OWNERSHIP_PENDING, 1L, t1);
        assertThat(domain.getOperationState()).isEqualTo(DomainOperationState.RUNNING);
        assertThat(domain.getVerifiedAt()).isNull();

        domain.transition(DomainStatus.CERTIFICATE_PENDING, 2L, t2);
        assertThat(domain.getVerifiedAt()).isEqualTo(t2);

        domain.transition(DomainStatus.TARGET_PENDING, 3L, t3);
        domain.transition(DomainStatus.ACTIVE, 4L, t4);
        assertThat(domain.getActivatedAt()).isEqualTo(t4);
        assertThat(domain.getOperationState()).isEqualTo(DomainOperationState.SUCCEEDED);

        domain.transition(DomainStatus.SUSPENDED, 5L, t5);
        assertThat(domain.getSuspendedAt()).isEqualTo(t5);
        assertThat(domain.getDirectoryVersion()).isEqualTo(5L);
    }

    @Test
    void rejectsNonHttpsOrWildcardFrontendOrigins() {
        TenantDirectoryService.validateHttpsOrigin("https://storefront.example.test");
        TenantDirectoryService.validateHttpsOrigin("https://storefront.example.test:8443");

        assertThatThrownBy(() -> TenantDirectoryService.validateHttpsOrigin("http://storefront.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDirectoryService.validateHttpsOrigin("https://*.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDirectoryService.validateHttpsOrigin("https://storefront.example.test/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
