package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.service.DeploymentTargetService;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Super-admin APIs for registering and verifying dedicated backend deployment targets. */
@RestController
@RequestMapping("/api/admin/deployment-targets")
public class DeploymentTargetAdminController {
    private final DeploymentTargetService targets;

    public DeploymentTargetAdminController(DeploymentTargetService targets) {
        this.targets = targets;
    }

    @GetMapping
    public List<DeploymentTargetView> list() {
        return targets.listTargets();
    }

    @GetMapping("/{targetId}")
    public DeploymentTargetView get(@PathVariable("targetId") String targetId) {
        return targets.getTarget(targetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentTargetView register(@Valid @RequestBody RegisterTargetRequest request) {
        return targets.registerTarget(
                request.id(),
                request.targetType(),
                request.protocol(),
                request.privateOrigin(),
                request.healthPath());
    }

    @PostMapping("/{targetId}/verify-health")
    public DeploymentTargetView verifyHealth(@PathVariable("targetId") String targetId) {
        return targets.verifyHealth(targetId);
    }

    @PutMapping("/{targetId}/state")
    public DeploymentTargetView updateState(
            @PathVariable("targetId") String targetId,
            @RequestBody UpdateTargetStateRequest request) {
        return targets.updateState(targetId, request.status(), request.capacity());
    }

    public record RegisterTargetRequest(
            @NotBlank String id,
            @NotNull DeploymentTargetType targetType,
            @NotBlank String protocol,
            @NotBlank String privateOrigin,
            String healthPath) { }

    public record UpdateTargetStateRequest(
            DeploymentTargetStatus status,
            DeploymentTargetCapacity capacity) { }
}
