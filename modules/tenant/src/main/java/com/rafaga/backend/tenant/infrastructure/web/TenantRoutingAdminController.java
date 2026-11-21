package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.service.TenantDirectoryService;
import com.rafaga.backend.tenant.domain.model.DomainStatus;
import com.rafaga.backend.tenant.domain.model.TenantDomainView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Super-admin control-plane APIs. They mutate state only; a gateway refreshes asynchronously. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/domains")
public class TenantRoutingAdminController {
    private final TenantDirectoryService directory;

    public TenantRoutingAdminController(TenantDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    public List<DomainResponse> list(@PathVariable("tenantId") String tenantId) {
        return directory.domains(tenantId).stream().map(DomainResponse::from).toList();
    }

    @GetMapping("/{domain}")
    public DomainResponse get(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain) {
        return DomainResponse.from(directory.domain(tenantId, domain));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DomainResponse request(
            @PathVariable("tenantId") String tenantId,
            @Valid @RequestBody RequestDomainRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return DomainResponse.from(directory.requestDomain(
                tenantId,
                request.hostname(),
                request.frontendOrigin(),
                request.targetId(),
                idempotencyKey));
    }

    @PutMapping("/{domain}/configuration")
    public VersionResponse configure(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain,
            @Valid @RequestBody ConfigureDomainRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new VersionResponse(directory.configure(
                tenantId,
                domain,
                request.frontendOrigin(),
                request.targetId(),
                idempotencyKey));
    }

    @PostMapping("/{domain}/transition")
    public VersionResponse transition(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain,
            @Valid @RequestBody TransitionDomainRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new VersionResponse(directory.transition(tenantId, domain, request.targetStatus(), idempotencyKey));
    }

    @PostMapping("/{domain}/activate")
    public VersionResponse activate(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new VersionResponse(directory.transition(tenantId, domain, DomainStatus.ACTIVE, idempotencyKey));
    }

    @PostMapping("/{domain}/suspend")
    public VersionResponse suspend(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new VersionResponse(directory.transition(tenantId, domain, DomainStatus.SUSPENDED, idempotencyKey));
    }

    @PostMapping("/{domain}/retry")
    public VersionResponse retry(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new VersionResponse(directory.transition(tenantId, domain, DomainStatus.REQUESTED, idempotencyKey));
    }

    @DeleteMapping("/{domain}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VersionResponse remove(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("domain") String domain,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return new VersionResponse(directory.transition(tenantId, domain, DomainStatus.REMOVING, idempotencyKey));
    }

    public record RequestDomainRequest(@NotBlank String hostname, String frontendOrigin, String targetId) { }

    public record ConfigureDomainRequest(@NotBlank String frontendOrigin, String targetId) { }

    public record TransitionDomainRequest(@NotNull DomainStatus targetStatus) { }

    public record VersionResponse(long directoryVersion) { }

    public record DomainResponse(
            String tenantId,
            String hostname,
            String status,
            String frontendOrigin,
            String targetId,
            String targetType,
            long directoryVersion,
            Instant requestedAt,
            Instant verifiedAt,
            Instant activatedAt,
            Instant suspendedAt,
            String operationState,
            Instant operationUpdatedAt) {
        static DomainResponse from(TenantDomainView view) {
            return new DomainResponse(
                    view.tenantId(),
                    view.hostname(),
                    view.status().name(),
                    view.frontendOrigin(),
                    view.targetId(),
                    view.targetType().name(),
                    view.directoryVersion(),
                    view.requestedAt(),
                    view.verifiedAt(),
                    view.activatedAt(),
                    view.suspendedAt(),
                    view.operationState().name(),
                    view.operationUpdatedAt());
        }
    }
}
