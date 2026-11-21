package com.rafaga.backend.tenant.application.service;

import com.rafaga.backend.tenant.application.port.R2CorsPort;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.R2CorsRevisionView;
import com.rafaga.backend.tenant.infrastructure.persistence.R2CorsRevisionEntity;
import com.rafaga.backend.tenant.infrastructure.persistence.R2CorsRevisionRepository;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControl;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantDomainControlRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Synchronizes explicit HTTPS origins to Cloudflare R2 CORS as a separate control-plane operation,
 * rejecting wildcards and retaining prior revisions for rollback.
 */
@Service
public class R2CorsSyncService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final TenantDomainControlRepository domains;
    private final R2CorsRevisionRepository revisions;
    private final R2CorsPort r2Cors;
    private final ObjectMapper objectMapper;
    private final String dashboardOriginsProperty;
    private final Clock clock;

    @Autowired
    public R2CorsSyncService(
            TenantDomainControlRepository domains,
            R2CorsRevisionRepository revisions,
            R2CorsPort r2Cors,
            ObjectMapper objectMapper,
            @Value("${rafaga.web.allowed-origins:}") String dashboardOriginsProperty) {
        this(domains, revisions, r2Cors, objectMapper, dashboardOriginsProperty, Clock.systemUTC());
    }

    R2CorsSyncService(
            TenantDomainControlRepository domains,
            R2CorsRevisionRepository revisions,
            R2CorsPort r2Cors,
            ObjectMapper objectMapper,
            String dashboardOriginsProperty,
            Clock clock) {
        this.domains = domains;
        this.revisions = revisions;
        this.r2Cors = r2Cors;
        this.objectMapper = objectMapper;
        this.dashboardOriginsProperty = dashboardOriginsProperty;
        this.clock = clock;
    }

    @Transactional
    public R2CorsRevisionView syncActiveOrigins(List<String> additionalExplicitOrigins) {
        Set<String> origins = new TreeSet<>();
        if (dashboardOriginsProperty != null && !dashboardOriginsProperty.isBlank()) {
            Arrays.stream(dashboardOriginsProperty.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(origins::add);
        }
        if (additionalExplicitOrigins != null) {
            additionalExplicitOrigins.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(origins::add);
        }
        for (TenantDomainControl domain : domains.findByStatus(DomainStatus.ACTIVE)) {
            if (domain.getFrontendOrigin() != null && !domain.getFrontendOrigin().isBlank()) {
                origins.add(domain.getFrontendOrigin().trim());
            }
        }
        List<String> validated = List.copyOf(origins);
        if (validated.isEmpty()) {
            throw new IllegalStateException("At least one explicit HTTPS origin is required for R2 CORS");
        }
        validated.forEach(TenantDirectoryService::validateHttpsOrigin);
        r2Cors.applyAllowedOrigins(validated);

        long nextVersion = revisions.findTopByOrderByVersionDesc()
                .map(latest -> latest.getVersion() + 1)
                .orElse(1L);
        Instant now = Instant.now(clock);
        R2CorsRevisionEntity saved = revisions.save(new R2CorsRevisionEntity(
                nextVersion,
                serialize(validated),
                now,
                null,
                "APPLIED"));
        return toView(saved);
    }

    @Transactional
    public R2CorsRevisionView rollbackToPrevious() {
        List<R2CorsRevisionEntity> history = revisions.findTop20ByOrderByVersionDesc();
        if (history.size() < 2) {
            throw new IllegalStateException("No previous R2 CORS revision is available for rollback");
        }
        R2CorsRevisionEntity latest = history.get(0);
        R2CorsRevisionEntity previous = history.get(1);
        List<String> previousOrigins = deserialize(previous.getOriginsJson());
        previousOrigins.forEach(TenantDirectoryService::validateHttpsOrigin);

        r2Cors.applyAllowedOrigins(previousOrigins);
        Instant now = Instant.now(clock);
        R2CorsRevisionEntity rolledBack = revisions.save(new R2CorsRevisionEntity(
                latest.getVersion() + 1,
                previous.getOriginsJson(),
                now,
                previous.getVersion(),
                "ROLLED_BACK"));
        return toView(rolledBack);
    }

    @Transactional(readOnly = true)
    public List<R2CorsRevisionView> revisions() {
        return revisions.findTop20ByOrderByVersionDesc().stream()
                .map(this::toView)
                .toList();
    }

    private String serialize(List<String> origins) {
        return objectMapper.writeValueAsString(origins);
    }

    private List<String> deserialize(String json) {
        return List.copyOf(objectMapper.readValue(json, STRING_LIST));
    }

    private R2CorsRevisionView toView(R2CorsRevisionEntity entity) {
        return new R2CorsRevisionView(
                entity.getVersion(),
                deserialize(entity.getOriginsJson()),
                entity.getAppliedAt(),
                entity.getRollbackOfVersion(),
                entity.getStatus());
    }
}
