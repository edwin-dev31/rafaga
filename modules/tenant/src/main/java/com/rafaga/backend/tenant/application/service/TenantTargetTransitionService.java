package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.port.DeploymentTargetHealthProbe;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.model.TenantTargetTransitionState;
import com.rafaga.backend.tenant.domain.model.TenantTargetTransitionView;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantTargetTransitionEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantTargetTransitionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates reversible tenant deployment-target transitions:
 * prepare -> data validation -> atomic activation -> drain -> rollback + audit.
 */
@Service
public class TenantTargetTransitionService {
    private final TenantTargetTransitionRepository transitions;
    private final TenantDomainControlRepository domains;
    private final DeploymentTargetRepository targets;
    private final TenantDirectoryStateRepository directoryState;
    private final DeploymentTargetHealthProbe healthProbe;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public TenantTargetTransitionService(
            TenantTargetTransitionRepository transitions,
            TenantDomainControlRepository domains,
            DeploymentTargetRepository targets,
            TenantDirectoryStateRepository directoryState,
            DeploymentTargetHealthProbe healthProbe,
            ApplicationEventPublisher events) {
        this(transitions, domains, targets, directoryState, healthProbe, events, Clock.systemUTC());
    }

    TenantTargetTransitionService(
            TenantTargetTransitionRepository transitions,
            TenantDomainControlRepository domains,
            DeploymentTargetRepository targets,
            TenantDirectoryStateRepository directoryState,
            DeploymentTargetHealthProbe healthProbe,
            ApplicationEventPublisher events,
            Clock clock) {
        this.transitions = transitions;
        this.domains = domains;
        this.targets = targets;
        this.directoryState = directoryState;
        this.healthProbe = healthProbe;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TenantTargetTransitionView prepare(String tenantId, String rawDomain, String toTargetId, String initiatedBy) {
        String domain = normalize(rawDomain);
        TenantDomainControl controlledDomain = domains.findByTenantIdAndDomain(tenantId, domain)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant domain"));
        DeploymentTargetEntity destination = targets.findById(toTargetId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown deployment target: " + toTargetId));
        if (!destination.getStatus().isRoutable()) {
            throw new IllegalStateException("Destination deployment target is not healthy/routable: " + toTargetId);
        }
        if (toTargetId.equals(controlledDomain.getTargetId())) {
            throw new IllegalStateException("Domain is already routed to target: " + toTargetId);
        }
        Instant now = Instant.now(clock);
        String actor = (initiatedBy == null || initiatedBy.isBlank()) ? "super-admin" : initiatedBy.trim();
        TenantTargetTransitionEntity saved = transitions.save(new TenantTargetTransitionEntity(
                UUID.randomUUID().toString(),
                tenantId,
                domain,
                controlledDomain.getTargetId(),
                toTargetId,
                actor,
                now));
        return toView(saved);
    }

    @Transactional
    public TenantTargetTransitionView validateData(String tenantId, String transitionId, String checksum) {
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("dataValidationChecksum is required");
        }
        TenantTargetTransitionEntity transition = requireTransitionForTenant(tenantId, transitionId);
        DeploymentTargetEntity destination = targets.findById(transition.getToTargetId())
                .orElseThrow(() -> new IllegalStateException("Destination deployment target no longer exists"));
        if (!destination.getStatus().isRoutable()
                || !healthProbe.isHealthy(destination.getProtocol(), destination.getPrivateOrigin(), destination.getHealthPath())) {
            throw new IllegalStateException("Destination deployment target failed live health verification");
        }
        transition.markValidated(checksum.trim(), Instant.now(clock));
        return toView(transition);
    }

    @Transactional
    public TenantTargetTransitionView activate(String tenantId, String transitionId) {
        TenantTargetTransitionEntity transition = requireTransitionForTenant(tenantId, transitionId);
        TenantDomainControl controlledDomain = domains.findByTenantIdAndDomain(tenantId, transition.getDomain())
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant domain"));
        DeploymentTargetEntity destination = targets.findById(transition.getToTargetId())
                .orElseThrow(() -> new IllegalStateException("Destination deployment target no longer exists"));
        if (!destination.getStatus().isRoutable()) {
            throw new IllegalStateException("Destination deployment target is not routable");
        }
        Instant now = Instant.now(clock);
        transition.markActivated(now);
        long version = advanceVersion(now);
        controlledDomain.configure(controlledDomain.getFrontendOrigin(), transition.getToTargetId(), version, now);
        events.publishEvent(new TenantDirectoryChangedEvent(version, controlledDomain.getDomain()));
        return toView(transition);
    }

    @Transactional
    public TenantTargetTransitionView drain(String tenantId, String transitionId) {
        TenantTargetTransitionEntity transition = requireTransitionForTenant(tenantId, transitionId);
        transition.markDrained(Instant.now(clock));
        return toView(transition);
    }

    @Transactional
    public TenantTargetTransitionView rollback(String tenantId, String transitionId, String reason) {
        TenantTargetTransitionEntity transition = requireTransitionForTenant(tenantId, transitionId);
        boolean wasActivated = transition.getState() == TenantTargetTransitionState.ACTIVATED;
        Instant now = Instant.now(clock);
        String safeReason = (reason == null || reason.isBlank()) ? "Operator initiated rollback" : reason.trim();
        transition.markRolledBack(safeReason, now);
        if (wasActivated) {
            TenantDomainControl controlledDomain = domains.findByTenantIdAndDomain(tenantId, transition.getDomain())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown tenant domain"));
            long version = advanceVersion(now);
            controlledDomain.configure(controlledDomain.getFrontendOrigin(), transition.getFromTargetId(), version, now);
            events.publishEvent(new TenantDirectoryChangedEvent(version, controlledDomain.getDomain()));
        }
        return toView(transition);
    }

    @Transactional(readOnly = true)
    public List<TenantTargetTransitionView> history(String tenantId) {
        return transitions.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(TenantTargetTransitionService::toView)
                .toList();
    }

    private TenantTargetTransitionEntity requireTransitionForTenant(String tenantId, String transitionId) {
        TenantTargetTransitionEntity entity = transitions.findById(transitionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown target transition: " + transitionId));
        if (!entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Transition does not belong to tenant");
        }
        return entity;
    }

    private long advanceVersion(Instant now) {
        return directoryState.findBySingletonId((short) 1)
                .orElseThrow(() -> new IllegalStateException("Tenant directory state row is missing"))
                .advance(now);
    }

    private static String normalize(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        return rawDomain.trim().toLowerCase(Locale.ROOT);
    }

    private static TenantTargetTransitionView toView(TenantTargetTransitionEntity entity) {
        return new TenantTargetTransitionView(
                entity.getId(),
                entity.getTenantId(),
                entity.getDomain(),
                entity.getFromTargetId(),
                entity.getToTargetId(),
                entity.getState(),
                entity.getDataValidationChecksum(),
                entity.getInitiatedBy(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt());
    }
}
