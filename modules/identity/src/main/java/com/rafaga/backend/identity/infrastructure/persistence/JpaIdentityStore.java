package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.Role;
import com.rafaga.backend.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaIdentityStore implements IdentityStore {
    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;

    JpaIdentityStore(UserJpaRepository userRepository, RoleJpaRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public Optional<User> findUserById(String tenantId, UUID userId) {
        return userRepository.findByIdAndTenantId(userId, tenantId);
    }

    @Override
    public Optional<User> findUserByKeycloakId(String tenantId, String keycloakId) {
        return userRepository.findByKeycloakIdAndTenantId(keycloakId, tenantId);
    }

    @Override
    public Optional<Role> findRoleById(String tenantId, UUID roleId) {
        return roleRepository.findByIdAndTenantId(roleId, tenantId);
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }
}
