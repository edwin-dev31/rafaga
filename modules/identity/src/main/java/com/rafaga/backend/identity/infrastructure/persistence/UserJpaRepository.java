package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface UserJpaRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByIdAndTenantId(UUID userId, String tenantId);

    Optional<User> findByKeycloakIdAndTenantId(String keycloakId, String tenantId);

    long countByTenantIdAndStatus(String tenantId, UserStatus status);
}
