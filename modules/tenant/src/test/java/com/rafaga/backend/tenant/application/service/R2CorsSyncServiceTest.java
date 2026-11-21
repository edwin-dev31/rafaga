package com.rafaga.backend.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.tenant.application.port.R2CorsPort;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.R2CorsRevisionView;
import com.rafaga.backend.tenant.infrastructure.persistence.R2CorsRevisionEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.R2CorsRevisionRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class R2CorsSyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T17:10:00Z");

    private TenantDomainControlRepository domains;
    private R2CorsRevisionRepository revisions;
    private R2CorsPort r2Cors;
    private List<R2CorsRevisionEntity> storedRevisions;
    private R2CorsSyncService service;

    @BeforeEach
    void setUp() {
        domains = mock(TenantDomainControlRepository.class);
        revisions = mock(R2CorsRevisionRepository.class);
        r2Cors = mock(R2CorsPort.class);
        storedRevisions = new ArrayList<>();

        when(revisions.save(any(R2CorsRevisionEntity.class))).thenAnswer(inv -> {
            R2CorsRevisionEntity entity = inv.getArgument(0);
            storedRevisions.add(entity);
            return entity;
        });
        when(revisions.findTopByOrderByVersionDesc()).thenAnswer(inv -> storedRevisions.stream()
                .max(Comparator.comparingLong(R2CorsRevisionEntity::getVersion)));
        when(revisions.findTop20ByOrderByVersionDesc()).thenAnswer(inv -> storedRevisions.stream()
                .sorted(Comparator.comparingLong(R2CorsRevisionEntity::getVersion).reversed())
                .limit(20)
                .toList());

        service = new R2CorsSyncService(
                domains,
                revisions,
                r2Cors,
                new ObjectMapper(),
                "https://app.rafaga.app",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void syncsExplicitHttpsOriginsAndSupportsRollbackToPriorRevision() {
        TenantDomainControl shopOne = new TenantDomainControl(
                "t1", "shop1.example.test", DomainStatus.ACTIVE, "https://shop1.example.test", "shared-pool", NOW);
        when(domains.findByStatus(DomainStatus.ACTIVE)).thenReturn(List.of(shopOne));

        R2CorsRevisionView v1 = service.syncActiveOrigins(List.of());
        assertThat(v1.version()).isEqualTo(1L);
        assertThat(v1.allowedOrigins()).containsExactly("https://app.rafaga.app", "https://shop1.example.test");
        verify(r2Cors).applyAllowedOrigins(List.of("https://app.rafaga.app", "https://shop1.example.test"));

        TenantDomainControl shopTwo = new TenantDomainControl(
                "t2", "shop2.example.test", DomainStatus.ACTIVE, "https://shop2.example.test", "shared-pool", NOW);
        when(domains.findByStatus(DomainStatus.ACTIVE)).thenReturn(List.of(shopOne, shopTwo));

        R2CorsRevisionView v2 = service.syncActiveOrigins(List.of());
        assertThat(v2.version()).isEqualTo(2L);
        assertThat(v2.allowedOrigins()).containsExactly(
                "https://app.rafaga.app", "https://shop1.example.test", "https://shop2.example.test");

        R2CorsRevisionView v3 = service.rollbackToPrevious();
        assertThat(v3.version()).isEqualTo(3L);
        assertThat(v3.rollbackOfVersion()).isEqualTo(1L);
        assertThat(v3.status()).isEqualTo("ROLLED_BACK");
        assertThat(v3.allowedOrigins()).containsExactly("https://app.rafaga.app", "https://shop1.example.test");
    }

    @Test
    void rejectsWildcardOrNonHttpsOrigins() {
        when(domains.findByStatus(DomainStatus.ACTIVE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.syncActiveOrigins(List.of("https://*.rafaga.app")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        assertThatThrownBy(() -> service.syncActiveOrigins(List.of("http://insecure.rafaga.app")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }
}
