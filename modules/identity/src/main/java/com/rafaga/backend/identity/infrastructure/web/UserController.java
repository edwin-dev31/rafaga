package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.service.RoleSummary;
import com.rafaga.backend.identity.application.service.UserProfile;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.identity.domain.model.Permission;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        return UserResponse.from(userService.getUserById(userId));
    }

    @GetMapping("/{userId}/roles")
    public List<RoleResponse> getRoles(@PathVariable UUID userId) {
        return userService.getUserRoles(userId).stream().map(RoleResponse::from).toList();
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@userService.hasCurrentUserPermission(T(com.rafaga.backend.identity.domain.model.Permission).USER_MANAGE)")
    public void assignRole(@PathVariable UUID userId, @PathVariable UUID roleId) {
        userService.assignRoleToUser(userId, roleId);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        return UserResponse.from(userService.getCurrentUser());
    }

    public record UserResponse(UUID id, String username, String email, UserStatus status) {
        static UserResponse from(UserProfile profile) {
            return new UserResponse(profile.id(), profile.username(), profile.email(), profile.status());
        }
    }

    public record RoleResponse(UUID id, String name, String description, Set<Permission> permissions) {
        static RoleResponse from(RoleSummary role) {
            return new RoleResponse(role.id(), role.name(), role.description(), role.permissions());
        }
    }
}
