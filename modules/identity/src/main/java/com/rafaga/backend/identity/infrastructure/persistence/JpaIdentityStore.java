package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaIdentityStore implements IdentityStore {
    private final UserJpaRepository userRepository;
    JpaIdentityStore(UserJpaRepository userRepository) {
        this.userRepository = userRepository;
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
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public long countUsers(String tenantId) {
        return userRepository.countByTenantId(tenantId);
    }
}
