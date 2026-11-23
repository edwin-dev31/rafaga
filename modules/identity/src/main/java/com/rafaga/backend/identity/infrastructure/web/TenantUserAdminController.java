package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.service.UserProfile;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.identity.domain.model.UserStatus;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SUPER_ADMIN endpoints for managing users belonging to a specific tenant without X-Registration-Token. */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/users")
public class TenantUserAdminController {
    private final TenantRegistry tenantRegistry;
    private final UserService userService;

    public TenantUserAdminController(TenantRegistry tenantRegistry, UserService userService) {
        this.tenantRegistry = tenantRegistry;
        this.userService = userService;
    }

    @GetMapping
    public Page<UserResponse> list(
            @PathVariable("tenantId") String tenantId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "status", required = false) UserStatus status,
            Pageable pageable) {
        return inTenant(tenantId, () -> userService.listTenantUsers(tenantId, query, status, pageable)
                .map(UserResponse::from));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @PathVariable("tenantId") String tenantId,
            @Valid @RequestBody CreateTenantUserRequest request) {
        return inTenant(tenantId, () -> UserResponse.from(userService.registerTenantUser(
                tenantId,
                new UserRegistration(
                        request.username(),
                        request.firstName(),
                        request.lastName(),
                        request.email(),
                        request.password()),
                request.role())));
    }

    @PostMapping("/{userId}/activate")
    public UserResponse activate(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("userId") UUID userId) {
        return inTenant(tenantId, () -> UserResponse.from(
                userService.setTenantUserStatus(tenantId, userId, UserStatus.ACTIVE)));
    }

    @PostMapping("/{userId}/deactivate")
    public UserResponse deactivate(
            @PathVariable("tenantId") String tenantId,
            @PathVariable("userId") UUID userId) {
        return inTenant(tenantId, () -> UserResponse.from(
                userService.setTenantUserStatus(tenantId, userId, UserStatus.DISABLED)));
    }

    private <T> T inTenant(String tenantId, Supplier<T> action) {
        tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new IdentityResourceNotFoundException("Tenant", tenantId));
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

    public record CreateTenantUserRequest(
            @NotBlank String username,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            String role) {
    }

    public record UserResponse(UUID id, String username, String email, UserStatus status) {
        static UserResponse from(UserProfile profile) {
            return new UserResponse(profile.id(), profile.username(), profile.email(), profile.status());
        }
    }
}
