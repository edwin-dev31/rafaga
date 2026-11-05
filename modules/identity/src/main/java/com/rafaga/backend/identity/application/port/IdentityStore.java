package com.rafaga.backend.identity.application.port;

import com.rafaga.backend.identity.domain.model.Role;
import com.rafaga.backend.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface IdentityStore {
    Optional<User> findUserById(String tenantId, UUID userId);

    Optional<User> findUserByKeycloakId(String tenantId, String keycloakId);

    Optional<Role> findRoleById(String tenantId, UUID roleId);

    User saveUser(User user);
}
