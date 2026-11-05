package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.identity.domain.model.Permission;
import com.rafaga.backend.identity.domain.model.Role;
import java.util.Set;
import java.util.UUID;

public record RoleSummary(UUID id, String name, String description, Set<Permission> permissions) {
    static RoleSummary from(Role role) {
        return new RoleSummary(role.getId(), role.getName(), role.getDescription(), role.getPermissions());
    }
}
