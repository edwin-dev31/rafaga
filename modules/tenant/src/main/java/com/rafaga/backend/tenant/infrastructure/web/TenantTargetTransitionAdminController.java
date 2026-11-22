package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.service.TenantTargetTransitionService;
import com.rafaga.backend.tenant.domain.model.TenantTargetTransitionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Super-admin APIs for controlled, reversible tenant target transitions. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/target-transitions")
public class TenantTargetTransitionAdminController {
    private final TenantTargetTransitionService transitions;

    public TenantTargetTransitionAdminController(TenantTargetTransitionService transitions) {
        this.transitions = transitions;
    }

    @GetMapping
    public List<TenantTargetTransitionView> history(@PathVariable("tenantId") String tenantId) {
        return transitions.history(tenantId);
    }

    @PostMapping("/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantTargetTransitionView prepare(
            @PathVariable("tenantId") String tenantId,
            @Valid @RequestBody PrepareTransitionRequest request,
            Principal principal) {
        String actor = principal == null ? "super-admin" : principal.getName();
        return transitions.prepare(tenantId, request.domain(), request.toTargetId(), actor);
    }

    @PostMapping("/{transitionId}/validate")
    public TenantTargetTransitionView validate(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("transitionId") String transitionId,
            @Valid @RequestBody ValidateTransitionRequest request) {
        return transitions.validateData(tenantId, transitionId, request.dataValidationChecksum());
    }

    @PostMapping("/{transitionId}/activate")
    public TenantTargetTransitionView activate(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("transitionId") String transitionId) {
        return transitions.activate(tenantId, transitionId);
    }

    @PostMapping("/{transitionId}/drain")
    public TenantTargetTransitionView drain(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("transitionId") String transitionId) {
        return transitions.drain(tenantId, transitionId);
    }

    @PostMapping("/{transitionId}/rollback")
    public TenantTargetTransitionView rollback(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("transitionId") String transitionId,
            @RequestBody(required = false) RollbackTransitionRequest request) {
        String reason = request == null ? null : request.reason();
        return transitions.rollback(tenantId, transitionId, reason);
    }

    public record PrepareTransitionRequest(@NotBlank String domain, @NotBlank String toTargetId) { }

    public record ValidateTransitionRequest(@NotBlank String dataValidationChecksum) { }

    public record RollbackTransitionRequest(String reason) { }
}
