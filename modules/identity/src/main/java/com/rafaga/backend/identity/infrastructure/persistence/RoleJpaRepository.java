package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.domain.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByIdAndTenantId(UUID roleId, String tenantId);
}
