package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndTenantId(UUID userId, String tenantId);

    Optional<User> findByKeycloakIdAndTenantId(String keycloakId, String tenantId);
}
