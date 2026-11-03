package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.command.TenantRegistration;
import com.rafaga.backend.tenant.application.command.TenantUpdate;
import com.rafaga.backend.tenant.application.service.TenantService;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenants")
public class TenantAdminController {
    private final TenantService tenantService;

    public TenantAdminController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse register(@Valid @RequestBody TenantRequest request) {
        return TenantResponse.from(tenantService.registerTenant(request.toRegistration()));
    }

    @GetMapping("/{tenantId}")
    public TenantResponse getById(@PathVariable String tenantId) {
        return TenantResponse.from(tenantService.getTenantById(tenantId));
    }

    @PutMapping("/{tenantId}")
    public TenantResponse update(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantRequest request) {
        return TenantResponse.from(tenantService.updateTenant(tenantId, request.toUpdate()));
    }

    @GetMapping
    public List<TenantResponse> list() {
        return tenantService.getAllTenants().stream()
                .map(TenantResponse::from)
                .toList();
    }

    public record TenantRequest(
            @NotBlank String name,
            @NotEmpty List<@NotBlank String> domains,
            DeploymentMode deploymentMode,
            String databaseConfigId,
            @NotBlank String planId) {
        TenantRegistration toRegistration() {
            return new TenantRegistration(name, domains, deploymentMode, databaseConfigId, planId);
        }

        TenantUpdate toUpdate() {
            return new TenantUpdate(name, domains, deploymentMode, databaseConfigId, planId);
        }
    }

    public record TenantResponse(
            String id,
            String name,
            List<String> domains,
            DeploymentMode deploymentMode,
            String databaseConfigId,
            String planId,
            String status,
            Map<String, Boolean> featureFlags) {
        static TenantResponse from(TenantConfig config) {
            return new TenantResponse(
                    config.tenantId(),
                    config.name(),
                    config.domains(),
                    config.deploymentMode(),
                    config.databaseConfig() == null ? null : config.databaseConfig().secretReference(),
                    config.planId(),
                    config.status().name(),
                    config.featureFlags());
        }
    }
}
