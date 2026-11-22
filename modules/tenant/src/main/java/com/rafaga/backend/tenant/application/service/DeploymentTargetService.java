package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.port.DeploymentTargetHealthProbe;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetView;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.DeploymentTargetRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages deployment target registration and health verification before routing traffic. */
@Service
public class DeploymentTargetService {
    private static final Pattern SAFE_TARGET_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{1,99}$");

    private final DeploymentTargetRepository targets;
    private final DeploymentTargetHealthProbe healthProbe;
    private final Clock clock;

    @Autowired
    public DeploymentTargetService(DeploymentTargetRepository targets, DeploymentTargetHealthProbe healthProbe) {
        this(targets, healthProbe, Clock.systemUTC());
    }

    DeploymentTargetService(DeploymentTargetRepository targets, DeploymentTargetHealthProbe healthProbe, Clock clock) {
        this.targets = targets;
        this.healthProbe = healthProbe;
        this.clock = clock;
    }

    @Transactional
    public DeploymentTargetView registerTarget(
            String id,
            DeploymentTargetType type,
            String protocol,
            String privateOrigin,
            String healthPath) {
        if (id == null || !SAFE_TARGET_ID.matcher(id.trim()).matches()) {
            throw new IllegalArgumentException("Invalid deployment target id");
        }
        String normalizedId = id.trim();
        if (targets.existsById(normalizedId)) {
            throw new IllegalStateException("Deployment target already exists: " + normalizedId);
        }
        String normalizedProtocol = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedProtocol) && !"https".equals(normalizedProtocol)) {
            throw new IllegalArgumentException("protocol must be http or https");
        }
        validatePrivateOrigin(privateOrigin, normalizedProtocol);
        String normalizedHealthPath = (healthPath == null || healthPath.isBlank())
                ? "/actuator/health/readiness"
                : healthPath.trim();
        if (!normalizedHealthPath.startsWith("/")) {
            throw new IllegalArgumentException("healthPath must start with '/'");
        }
        Instant now = Instant.now(clock);
        DeploymentTargetEntity saved = targets.save(new DeploymentTargetEntity(
                normalizedId,
                type,
                normalizedProtocol,
                privateOrigin.trim(),
                normalizedHealthPath,
                now));
        return toView(saved);
    }

    @Transactional
    public DeploymentTargetView verifyHealth(String targetId) {
        DeploymentTargetEntity target = targets.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown deployment target: " + targetId));
        boolean healthy = healthProbe.isHealthy(target.getProtocol(), target.getPrivateOrigin(), target.getHealthPath());
        DeploymentTargetStatus nextStatus = healthy ? DeploymentTargetStatus.HEALTHY : DeploymentTargetStatus.UNHEALTHY;
        target.mark(nextStatus, target.getCapacity(), Instant.now(clock));
        return toView(target);
    }

    @Transactional
    public DeploymentTargetView updateState(
            String targetId,
            DeploymentTargetStatus status,
            DeploymentTargetCapacity capacity) {
        DeploymentTargetEntity target = targets.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown deployment target: " + targetId));
        target.mark(
                status != null ? status : target.getStatus(),
                capacity != null ? capacity : target.getCapacity(),
                Instant.now(clock));
        return toView(target);
    }

    @Transactional(readOnly = true)
    public List<DeploymentTargetView> listTargets() {
        return targets.findAll().stream().map(DeploymentTargetService::toView).toList();
    }

    @Transactional(readOnly = true)
    public DeploymentTargetView getTarget(String targetId) {
        DeploymentTargetEntity target = targets.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown deployment target: " + targetId));
        return toView(target);
    }

    private static void validatePrivateOrigin(String origin, String expectedProtocol) {
        if (origin == null || origin.isBlank() || origin.contains("*")) {
            throw new IllegalArgumentException("privateOrigin must be a valid URL without wildcards");
        }
        URI uri;
        try {
            uri = URI.create(origin.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("privateOrigin must be a valid URL");
        }
        if (!expectedProtocol.equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("privateOrigin must match protocol and contain no credentials or query");
        }
    }

    private static DeploymentTargetView toView(DeploymentTargetEntity target) {
        return new DeploymentTargetView(
                target.getId(),
                target.getTargetType(),
                target.getProtocol(),
                target.getPrivateOrigin(),
                target.getHealthPath(),
                target.getStatus(),
                target.getCapacity(),
                target.getUpdatedAt());
    }
}
