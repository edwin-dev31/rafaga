package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimits;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import com.rafaga.backend.tenant.application.exception.TenantNotFoundException;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetView;
import com.rafaga.backend.tenant.domain.model.DomainOperationState;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.TenantDirectoryEntry;
import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import com.rafaga.backend.tenant.domain.model.TenantDomainView;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryState;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperation;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperationRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Control-plane service and versioned read model for the future gateway. */
@Service
public class TenantDirectoryService {
    public static final String DEFAULT_SHARED_POOL_TARGET_ID = "shared-pool";

    private final TenantDomainControlRepository domains;
    private final TenantDirectoryStateRepository state;
    private final DeploymentTargetRepository targets;
    private final TenantDomainOperationRepository operations;
    private final TenantRegistry tenantRegistry;
    private final ApplicationEventPublisher events;
    private final TenantLimitsQuery limitsQuery;
    private final Clock clock;

    public TenantDirectoryService(
            TenantDomainControlRepository domains,
            TenantDirectoryStateRepository state,
            DeploymentTargetRepository targets,
            TenantDomainOperationRepository operations,
            TenantRegistry tenantRegistry,
            ApplicationEventPublisher events) {
        this(domains, state, targets, operations, tenantRegistry, events, (TenantLimitsQuery) null);
    }

    @Autowired
    public TenantDirectoryService(
            TenantDomainControlRepository domains,
            TenantDirectoryStateRepository state,
            DeploymentTargetRepository targets,
            TenantDomainOperationRepository operations,
            TenantRegistry tenantRegistry,
            ApplicationEventPublisher events,
            ObjectProvider<TenantLimitsQuery> limitsQueryProvider) {
        this(domains, state, targets, operations, tenantRegistry, events, limitsQueryProvider.getIfAvailable());
    }

    TenantDirectoryService(
            TenantDomainControlRepository domains,
            TenantDirectoryStateRepository state,
            DeploymentTargetRepository targets,
            TenantDomainOperationRepository operations,
            TenantRegistry tenantRegistry,
            ApplicationEventPublisher events,
            TenantLimitsQuery limitsQuery) {
        this.domains = domains;
        this.state = state;
        this.targets = targets;
        this.operations = operations;
        this.tenantRegistry = tenantRegistry;
        this.events = events;
        this.limitsQuery = limitsQuery;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public TenantDirectorySnapshot snapshot(long afterVersion) {
        boolean delta = afterVersion >= 0;
        long currentVersion = state.findById((short) 1)
                .map(TenantDirectoryState::getCurrentVersion)
                .orElse(0L);
        Map<String, DeploymentTargetEntity> targetsById = targets.findAll().stream()
                .collect(Collectors.toMap(DeploymentTargetEntity::getId, Function.identity()));
        List<TenantDomainControl> candidates = delta
                ? domains.findByDirectoryVersionGreaterThanOrderByDirectoryVersion(afterVersion)
                : domains.findByStatus(DomainStatus.ACTIVE);

        List<TenantDirectoryEntry> activeEntries = new ArrayList<>();
        List<String> removedHostnames = new ArrayList<>();

        for (TenantDomainControl domain : candidates) {
            DeploymentTargetEntity target = domain.getTargetId() == null
                    ? null
                    : targetsById.get(domain.getTargetId());
            boolean targetRoutable = domain.getTargetId() == null
                    || (target != null && target.getStatus().isRoutable());
            if (domain.getStatus() == DomainStatus.ACTIVE && targetRoutable) {
                DeploymentTargetType targetType = target == null
                        ? DeploymentTargetType.SHARED_POOL
                        : target.getTargetType();
                activeEntries.add(new TenantDirectoryEntry(
                        domain.getDomain(),
                        domain.getTenantId(),
                        domain.getFrontendOrigin(),
                        targetType,
                        domain.getTargetId(),
                        domain.getDirectoryVersion()));
            } else if (delta) {
                removedHostnames.add(domain.getDomain());
            }
        }
        return new TenantDirectorySnapshot(
                currentVersion,
                delta,
                List.copyOf(activeEntries),
                List.copyOf(removedHostnames));
    }

    @Transactional
    public TenantDomainView requestDomain(
            String tenantId,
            String rawDomain,
            String frontendOrigin,
            String requestedTargetId,
            String idempotencyKey) {
        tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        String domain = normalizeDomain(rawDomain);
        String resolvedTargetId = resolveTargetId(requestedTargetId);
        if (frontendOrigin != null && !frontendOrigin.isBlank()) {
            validateHttpsOrigin(frontendOrigin);
        }
        Optional<TenantDomainOperation> existingOp = findCompletedOperation(tenantId, domain, "REQUEST", idempotencyKey);
        if (existingOp.isPresent()) {
            return domain(tenantId, domain);
        }
        Optional<TenantDomainControl> existingDomain = domains.findByDomain(domain);
        if (existingDomain.isPresent()) {
            if (existingDomain.get().getTenantId().equals(tenantId)) {
                throw new IllegalStateException("Domain already registered for tenant: " + domain);
            }
            throw new IllegalStateException("Domain is already owned by another tenant");
        }
        if (limitsQuery != null) {
            TenantLimits limits = limitsQuery.getLimits(tenantId);
            long currentDomains = domains.countByTenantId(tenantId);
            if (currentDomains >= limits.maxDomains()) {
                throw new TenantLimitExceededException("domains", limits.maxDomains(), currentDomains);
            }
        }
        Instant now = Instant.now(clock);
        long version = advanceVersion(now);
        TenantDomainControl created = new TenantDomainControl(
                tenantId,
                domain,
                DomainStatus.REQUESTED,
                frontendOrigin == null || frontendOrigin.isBlank() ? null : frontendOrigin.trim(),
                resolvedTargetId,
                now);
        created.configure(created.getFrontendOrigin(), created.getTargetId(), version, now);
        domains.save(created);
        recordCompletedOperation(tenantId, domain, "REQUEST", idempotencyKey, version, now);
        events.publishEvent(new TenantDirectoryChangedEvent(version, domain));
        return toView(created, targetTypeFor(created.getTargetId()));
    }

    @Transactional
    public long configure(
            String tenantId,
            String rawDomain,
            String frontendOrigin,
            String requestedTargetId,
            String idempotencyKey) {
        String domain = normalizeDomain(rawDomain);
        validateHttpsOrigin(frontendOrigin);
        TenantDomainControl controlledDomain = domains.findByTenantIdAndDomain(tenantId, domain)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant domain"));
        String resolvedTargetId = resolveTargetId(requestedTargetId != null ? requestedTargetId : controlledDomain.getTargetId());

        Optional<TenantDomainOperation> existingOp = findCompletedOperation(tenantId, domain, "CONFIGURE", idempotencyKey);
        if (existingOp.isPresent() && existingOp.get().getDirectoryVersion() != null) {
            return existingOp.get().getDirectoryVersion();
        }
        if (Objects.equals(controlledDomain.getFrontendOrigin(), frontendOrigin.trim())
                && Objects.equals(controlledDomain.getTargetId(), resolvedTargetId)) {
            Instant now = Instant.now(clock);
            recordCompletedOperation(tenantId, domain, "CONFIGURE", idempotencyKey, controlledDomain.getDirectoryVersion(), now);
            return controlledDomain.getDirectoryVersion();
        }
        Instant now = Instant.now(clock);
        long version = advanceVersion(now);
        controlledDomain.configure(frontendOrigin.trim(), resolvedTargetId, version, now);
        recordCompletedOperation(tenantId, domain, "CONFIGURE", idempotencyKey, version, now);
        events.publishEvent(new TenantDirectoryChangedEvent(version, domain));
        return version;
    }

    @Transactional
    public long transition(String tenantId, String rawDomain, DomainStatus target, String idempotencyKey) {
        String domain = normalizeDomain(rawDomain);
        String operationType = "TRANSITION_" + target.name();
        TenantDomainControl controlledDomain = domains.findByTenantIdAndDomain(tenantId, domain)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant domain"));

        Optional<TenantDomainOperation> existingOp = findCompletedOperation(tenantId, domain, operationType, idempotencyKey);
        if (existingOp.isPresent() && existingOp.get().getDirectoryVersion() != null) {
            return existingOp.get().getDirectoryVersion();
        }
        if (controlledDomain.getStatus() == target) {
            Instant now = Instant.now(clock);
            recordCompletedOperation(tenantId, domain, operationType, idempotencyKey, controlledDomain.getDirectoryVersion(), now);
            return controlledDomain.getDirectoryVersion();
        }
        if (target == DomainStatus.ACTIVE) {
            validateReadyForActivation(controlledDomain);
        }
        Instant now = Instant.now(clock);
        long version = advanceVersion(now);
        controlledDomain.transition(target, version, now);
        recordCompletedOperation(tenantId, domain, operationType, idempotencyKey, version, now);
        events.publishEvent(new TenantDirectoryChangedEvent(version, domain));
        return version;
    }

    @Transactional
    public void suspendAllDomainsForTenant(String tenantId) {
        List<TenantDomainControl> tenantDomains = domains.findByTenantIdOrderByDomain(tenantId);
        Instant now = Instant.now(clock);
        for (TenantDomainControl domain : tenantDomains) {
            if (domain.getStatus() == DomainStatus.ACTIVE) {
                long version = advanceVersion(now);
                domain.transition(DomainStatus.SUSPENDED, version, now);
                events.publishEvent(new TenantDirectoryChangedEvent(version, domain.getDomain()));
            }
        }
    }

    @Transactional
    public void reactivateSuspendedDomainsForTenant(String tenantId) {
        List<TenantDomainControl> tenantDomains = domains.findByTenantIdOrderByDomain(tenantId);
        Instant now = Instant.now(clock);
        for (TenantDomainControl domain : tenantDomains) {
            if (domain.getStatus() == DomainStatus.SUSPENDED
                    && domain.getFrontendOrigin() != null
                    && !domain.getFrontendOrigin().isBlank()) {
                long version = advanceVersion(now);
                domain.transition(DomainStatus.ACTIVE, version, now);
                events.publishEvent(new TenantDirectoryChangedEvent(version, domain.getDomain()));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TenantDomainView> domains(String tenantId) {
        Map<String, DeploymentTargetType> targetTypes = targets.findAll().stream()
                .collect(Collectors.toMap(DeploymentTargetEntity::getId, DeploymentTargetEntity::getTargetType));
        return domains.findByTenantIdOrderByDomain(tenantId).stream()
                .map(domain -> toView(domain, targetTypes.getOrDefault(domain.getTargetId(), DeploymentTargetType.SHARED_POOL)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantDomainView domain(String tenantId, String rawDomain) {
        String domain = normalizeDomain(rawDomain);
        TenantDomainControl controlledDomain = domains.findByTenantIdAndDomain(tenantId, domain)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant domain"));
        return toView(controlledDomain, targetTypeFor(controlledDomain.getTargetId()));
    }

    @Transactional(readOnly = true)
    public List<DeploymentTargetView> targets() {
        return targets.findAll().stream()
                .map(target -> new DeploymentTargetView(
                        target.getId(),
                        target.getTargetType(),
                        target.getProtocol(),
                        target.getPrivateOrigin(),
                        target.getHealthPath(),
                        target.getStatus(),
                        target.getCapacity(),
                        target.getUpdatedAt()))
                .toList();
    }

    private void validateReadyForActivation(TenantDomainControl domain) {
        if (domain.getFrontendOrigin() == null || domain.getFrontendOrigin().isBlank()) {
            throw new IllegalStateException("Cannot activate domain without a verified frontendOrigin");
        }
        String targetId = domain.getTargetId();
        if (targetId != null) {
            DeploymentTargetEntity target = targets.findById(targetId)
                    .orElseThrow(() -> new IllegalStateException("Deployment target not found: " + targetId));
            if (!target.getStatus().isRoutable()) {
                throw new IllegalStateException("Deployment target is not healthy/routable: " + targetId);
            }
        }
    }

    private String resolveTargetId(String requestedTargetId) {
        String targetId = (requestedTargetId == null || requestedTargetId.isBlank())
                ? DEFAULT_SHARED_POOL_TARGET_ID
                : requestedTargetId.trim();
        if (!targets.existsById(targetId)) {
            throw new IllegalArgumentException("Unknown deployment target: " + targetId);
        }
        return targetId;
    }

    private DeploymentTargetType targetTypeFor(String targetId) {
        if (targetId == null) {
            return DeploymentTargetType.SHARED_POOL;
        }
        return targets.findById(targetId)
                .map(DeploymentTargetEntity::getTargetType)
                .orElse(DeploymentTargetType.SHARED_POOL);
    }

    private long advanceVersion(Instant now) {
        return state.findBySingletonId((short) 1)
                .orElseThrow(() -> new IllegalStateException("Tenant directory state row is missing"))
                .advance(now);
    }

    private Optional<TenantDomainOperation> findCompletedOperation(
            String tenantId, String domain, String operationType, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return operations.findByTenantIdAndDomainAndTypeAndKey(tenantId, domain, operationType, idempotencyKey.trim())
                .filter(op -> op.getState() == DomainOperationState.SUCCEEDED);
    }

    private void recordCompletedOperation(
            String tenantId, String domain, String operationType, String idempotencyKey, long version, Instant now) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        String key = idempotencyKey.trim();
        if (operations.findByTenantIdAndDomainAndTypeAndKey(tenantId, domain, operationType, key).isPresent()) {
            return;
        }
        TenantDomainOperation operation = new TenantDomainOperation(
                UUID.randomUUID().toString(), tenantId, domain, operationType, key, now);
        operation.complete(version, now);
        operations.save(operation);
    }

    private static String normalizeDomain(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        String normalized = rawDomain.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("://") || normalized.contains("/") || normalized.contains("*") || normalized.contains(" ")) {
            throw new IllegalArgumentException("Invalid hostname: " + rawDomain);
        }
        return normalized;
    }

    public static void validateHttpsOrigin(String origin) {
        if (origin == null || origin.isBlank() || origin.contains("*")) {
            throw new IllegalArgumentException("frontendOrigin must be an explicit absolute HTTPS origin");
        }
        URI uri;
        try {
            uri = URI.create(origin.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("frontendOrigin must be an explicit absolute HTTPS origin");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("frontendOrigin must be an explicit absolute HTTPS origin");
        }
    }

    private static TenantDomainView toView(TenantDomainControl domain, DeploymentTargetType targetType) {
        return new TenantDomainView(
                domain.getTenantId(),
                domain.getDomain(),
                domain.getStatus(),
                domain.getFrontendOrigin(),
                domain.getTargetId(),
                targetType,
                domain.getDirectoryVersion(),
                domain.getRequestedAt(),
                domain.getVerifiedAt(),
                domain.getActivatedAt(),
                domain.getSuspendedAt(),
                domain.getOperationState(),
                domain.getOperationUpdatedAt());
    }
}
