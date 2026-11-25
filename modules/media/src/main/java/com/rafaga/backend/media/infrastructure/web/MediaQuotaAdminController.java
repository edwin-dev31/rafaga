package com.rafaga.backend.media.infrastructure.web;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.media.application.exception.MediaValidationException;
import com.rafaga.backend.media.application.service.MediaService;
import com.rafaga.backend.media.domain.model.TenantMediaQuota;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Platform-only quota administration with an explicit tenant-scoped context. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/media-quota")
public class MediaQuotaAdminController {

    private final TenantRegistry tenantRegistry;
    private final MediaService mediaService;

    public MediaQuotaAdminController(TenantRegistry tenantRegistry, MediaService mediaService) {
        this.tenantRegistry = tenantRegistry;
        this.mediaService = mediaService;
    }

    @GetMapping
    public QuotaResponse getQuota(@PathVariable("tenantId") String tenantId) {
        return inTenant(tenantId, () -> QuotaResponse.from(mediaService.getQuotaStats()));
    }

    @PutMapping
    public QuotaResponse updateQuota(
            @PathVariable("tenantId") String tenantId,
            @Valid @RequestBody UpdateQuotaRequest request) {
        return inTenant(tenantId, () -> QuotaResponse.from(mediaService.updateQuotaLimits(
                request.maxAssets(), request.maxStorageBytes())));
    }

    private <T> T inTenant(String tenantId, Supplier<T> action) {
        tenantRegistry.findById(tenantId).orElseThrow(() -> tenantNotFound(tenantId));
        String previousTenantId = TenantContext.isSet() ? TenantContext.getCurrentTenantId() : null;
        TenantContext.setCurrentTenantId(tenantId);
        try {
            return action.get();
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(previousTenantId);
            }
        }
    }

    @ExceptionHandler(MediaValidationException.class)
    ResponseEntity<com.rafaga.backend.common.application.ApiError> handleValidation(MediaValidationException exception) {
        return ResponseEntity.badRequest()
                .body(com.rafaga.backend.common.application.ApiError.of(exception.getCode(), exception.getMessage()));
    }

    public record UpdateQuotaRequest(
            @NotNull @PositiveOrZero Long maxAssets,
            @NotNull @PositiveOrZero Long maxStorageBytes) {
    }

    public record QuotaResponse(
            String tenantId,
            long maxAssets,
            long maxStorageBytes,
            long usedAssets,
            long usedStorageBytes,
            long reservedAssets,
            long reservedBytes,
            long effectiveUsedAssets,
            long effectiveUsedBytes) {
        static QuotaResponse from(TenantMediaQuota quota) {
            return new QuotaResponse(
                    quota.tenantId(), quota.maxAssets(), quota.maxStorageBytes(),
                    quota.usedAssets(), quota.usedStorageBytes(), quota.reservedAssets(),
                    quota.reservedBytes(), quota.effectiveUsedAssets(), quota.effectiveUsedBytes());
        }
    }

    private ResponseStatusException tenantNotFound(String tenantId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
    }
}
