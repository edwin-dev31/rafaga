package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.common.application.JobOutcomeRegistry;
import com.rafaga.backend.tenant.application.port.CloudflareHostnamePort;
import com.rafaga.backend.tenant.application.port.CloudflareProvisioningException;
import com.rafaga.backend.tenant.application.port.CustomHostnameStatus;
import com.rafaga.backend.tenant.domain.event.TenantDirectoryChangedEvent;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDirectoryStateRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperation;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainOperationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances pending tenant domains through Cloudflare ownership/TLS checks and target validation
 * using bounded exponential backoff, idempotent operation records, and safe failure states.
 */
@Service
public class DomainActivationWorker {
    public static final String JOB_NAME = "tenant.domain-activation";
    static final int MAX_RETRIES = 10;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    private static final EnumSet<DomainStatus> ACTIONABLE_STATUSES = EnumSet.of(
            DomainStatus.REQUESTED,
            DomainStatus.OWNERSHIP_PENDING,
            DomainStatus.CERTIFICATE_PENDING,
            DomainStatus.TARGET_PENDING,
            DomainStatus.REMOVING);

    private static final Logger log = LoggerFactory.getLogger(DomainActivationWorker.class);

    private final TenantDomainControlRepository domains;
    private final TenantDirectoryStateRepository directoryState;
    private final DeploymentTargetRepository targets;
    private final TenantDomainOperationRepository operations;
    private final CloudflareHostnamePort cloudflare;
    private final ApplicationEventPublisher events;
    private final JobOutcomeRegistry jobOutcomes;
    private final Clock clock;

    @Autowired
    public DomainActivationWorker(
            TenantDomainControlRepository domains,
            TenantDirectoryStateRepository directoryState,
            DeploymentTargetRepository targets,
            TenantDomainOperationRepository operations,
            CloudflareHostnamePort cloudflare,
            ApplicationEventPublisher events,
            JobOutcomeRegistry jobOutcomes) {
        this(domains, directoryState, targets, operations, cloudflare, events, jobOutcomes, Clock.systemUTC());
    }

    DomainActivationWorker(
            TenantDomainControlRepository domains,
            TenantDirectoryStateRepository directoryState,
            DeploymentTargetRepository targets,
            TenantDomainOperationRepository operations,
            CloudflareHostnamePort cloudflare,
            ApplicationEventPublisher events,
            JobOutcomeRegistry jobOutcomes,
            Clock clock) {
        this.domains = domains;
        this.directoryState = directoryState;
        this.targets = targets;
        this.operations = operations;
        this.cloudflare = cloudflare;
        this.events = events;
        this.jobOutcomes = jobOutcomes;
        this.clock = clock;
    }

    @Transactional
    public int processDueDomains() {
        Instant now = Instant.now(clock);
        List<TenantDomainControl> due = domains.findByStatusInOrderByRequestedAtAsc(ACTIONABLE_STATUSES).stream()
                .filter(domain -> domain.getNextRetryAt() == null || !domain.getNextRetryAt().isAfter(now))
                .toList();
        int[] processed = {0};
        jobOutcomes.execute(JOB_NAME, () -> {
            for (TenantDomainControl domain : due) {
                processSingleDomain(domain, now);
                processed[0]++;
            }
        });
        return processed[0];
    }

    @Transactional
    public void processSingleDomain(TenantDomainControl domain, Instant now) {
        String opType = "WORKER_" + domain.getStatus().name();
        String idempotencyKey = domain.getStatus().name().toLowerCase() + "-attempt-" + domain.getRetryCount();
        try {
            switch (domain.getStatus()) {
                case REQUESTED -> handleRequested(domain, opType, idempotencyKey, now);
                case OWNERSHIP_PENDING -> handleOwnershipPending(domain, opType, idempotencyKey, now);
                case CERTIFICATE_PENDING -> handleCertificatePending(domain, opType, idempotencyKey, now);
                case TARGET_PENDING -> handleTargetPending(domain, opType, idempotencyKey, now);
                case REMOVING -> handleRemoving(domain, opType, idempotencyKey, now);
                default -> { }
            }
        } catch (CloudflareProvisioningException ex) {
            log.warn("Cloudflare provisioning error for domain={} code={} retryable={}",
                    domain.getDomain(), ex.failureCode(), ex.retryable());
            recordFailedOperation(domain, opType, idempotencyKey, ex.failureCode(), now);
            if (!ex.retryable() || domain.getRetryCount() + 1 >= MAX_RETRIES) {
                failDomain(domain, ex.failureCode(), now);
            } else {
                domain.scheduleRetry(ex.failureCode(), now.plus(backoffFor(domain.getRetryCount())), now);
            }
        }
    }

    private void handleRequested(TenantDomainControl domain, String opType, String key, Instant now) {
        CustomHostnameStatus status = cloudflare.createOrGetCustomHostname(domain.getDomain());
        domain.recordExternalHostnameId(status.externalId(), now);
        if (status.failed()) {
            failDomain(domain, safeCode(status.failureCode(), "CLOUDFLARE_HOSTNAME_FAILED"), now);
            recordFailedOperation(domain, opType, key, domain.getLastFailureCode(), now);
            return;
        }
        long version = advanceAndTransition(domain, DomainStatus.OWNERSHIP_PENDING, now);
        domain.clearRetry(now);
        recordSucceededOperation(domain, opType, key, version, now);
    }

    private void handleOwnershipPending(TenantDomainControl domain, String opType, String key, Instant now) {
        Optional<CustomHostnameStatus> remote = cloudflare.getCustomHostname(domain.getExternalHostnameId(), domain.getDomain());
        if (remote.isEmpty()) {
            handleRetryOrFail(domain, opType, key, "CLOUDFLARE_HOSTNAME_MISSING", now);
            return;
        }
        CustomHostnameStatus status = remote.get();
        if (domain.getExternalHostnameId() == null && status.externalId() != null) {
            domain.recordExternalHostnameId(status.externalId(), now);
        }
        if (status.failed()) {
            String code = safeCode(status.failureCode(), "OWNERSHIP_VERIFICATION_FAILED");
            failDomain(domain, code, now);
            recordFailedOperation(domain, opType, key, code, now);
            return;
        }
        if (!status.ownershipVerified()) {
            handleRetryOrFail(domain, opType, key, "OWNERSHIP_PENDING", now);
            return;
        }
        long version = advanceAndTransition(domain, DomainStatus.CERTIFICATE_PENDING, now);
        domain.clearRetry(now);
        recordSucceededOperation(domain, opType, key, version, now);
    }

    private void handleCertificatePending(TenantDomainControl domain, String opType, String key, Instant now) {
        Optional<CustomHostnameStatus> remote = cloudflare.getCustomHostname(domain.getExternalHostnameId(), domain.getDomain());
        if (remote.isEmpty()) {
            handleRetryOrFail(domain, opType, key, "CLOUDFLARE_HOSTNAME_MISSING", now);
            return;
        }
        CustomHostnameStatus status = remote.get();
        if (status.failed()) {
            String code = safeCode(status.failureCode(), "CERTIFICATE_ISSUANCE_FAILED");
            failDomain(domain, code, now);
            recordFailedOperation(domain, opType, key, code, now);
            return;
        }
        if (!status.certificateReady()) {
            handleRetryOrFail(domain, opType, key, "CERTIFICATE_PENDING", now);
            return;
        }
        long version = advanceAndTransition(domain, DomainStatus.TARGET_PENDING, now);
        domain.clearRetry(now);
        recordSucceededOperation(domain, opType, key, version, now);
    }

    private void handleTargetPending(TenantDomainControl domain, String opType, String key, Instant now) {
        if (domain.getFrontendOrigin() == null || domain.getFrontendOrigin().isBlank()) {
            handleRetryOrFail(domain, opType, key, "FRONTEND_ORIGIN_MISSING", now);
            return;
        }
        if (domain.getTargetId() != null) {
            Optional<DeploymentTargetEntity> target = targets.findById(domain.getTargetId());
            if (target.isEmpty() || !target.get().getStatus().isRoutable()) {
                handleRetryOrFail(domain, opType, key, "DEPLOYMENT_TARGET_NOT_ROUTABLE", now);
                return;
            }
        }
        long version = advanceAndTransition(domain, DomainStatus.ACTIVE, now);
        domain.clearRetry(now);
        recordSucceededOperation(domain, opType, key, version, now);
    }

    private void handleRemoving(TenantDomainControl domain, String opType, String key, Instant now) {
        if (domain.getExternalHostnameId() != null && !domain.getExternalHostnameId().isBlank()) {
            cloudflare.deleteCustomHostname(domain.getExternalHostnameId());
            domain.recordExternalHostnameId(null, now);
        }
        recordSucceededOperation(domain, opType, key, domain.getDirectoryVersion(), now);
        domains.delete(domain);
    }

    private void handleRetryOrFail(TenantDomainControl domain, String opType, String key, String code, Instant now) {
        if (domain.getRetryCount() + 1 >= MAX_RETRIES) {
            failDomain(domain, code + "_TIMEOUT", now);
            recordFailedOperation(domain, opType, key, code + "_TIMEOUT", now);
        } else {
            domain.scheduleRetry(code, now.plus(backoffFor(domain.getRetryCount())), now);
        }
    }

    private void failDomain(TenantDomainControl domain, String failureCode, Instant now) {
        long version = advanceVersion(now);
        domain.markFailed(failureCode, version, now);
        events.publishEvent(new TenantDirectoryChangedEvent(version, domain.getDomain()));
    }

    private long advanceAndTransition(TenantDomainControl domain, DomainStatus next, Instant now) {
        long version = advanceVersion(now);
        domain.transition(next, version, now);
        events.publishEvent(new TenantDirectoryChangedEvent(version, domain.getDomain()));
        return version;
    }

    private long advanceVersion(Instant now) {
        return directoryState.findBySingletonId((short) 1)
                .orElseThrow(() -> new IllegalStateException("Tenant directory state row is missing"))
                .advance(now);
    }

    private void recordSucceededOperation(TenantDomainControl domain, String type, String key, long version, Instant now) {
        if (operations.findByTenantIdAndDomainAndTypeAndKey(domain.getTenantId(), domain.getDomain(), type, key).isPresent()) {
            return;
        }
        TenantDomainOperation op = new TenantDomainOperation(
                UUID.randomUUID().toString(), domain.getTenantId(), domain.getDomain(), type, key, now);
        op.complete(version, now);
        operations.save(op);
    }

    private void recordFailedOperation(TenantDomainControl domain, String type, String key, String failureCode, Instant now) {
        if (operations.findByTenantIdAndDomainAndTypeAndKey(domain.getTenantId(), domain.getDomain(), type, key).isPresent()) {
            return;
        }
        TenantDomainOperation op = new TenantDomainOperation(
                UUID.randomUUID().toString(), domain.getTenantId(), domain.getDomain(), type, key, now);
        op.fail(failureCode, now);
        operations.save(op);
    }

    static Duration backoffFor(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 10);
        Duration candidate = BASE_BACKOFF.multipliedBy(multiplier);
        return candidate.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : candidate;
    }

    private static String safeCode(String code, String fallback) {
        return (code == null || code.isBlank()) ? fallback : code;
    }
}
