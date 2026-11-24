package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndTenantId(UUID userId, String tenantId);

    Optional<User> findByKeycloakIdAndTenantId(String keycloakId, String tenantId);

    long countByTenantId(String tenantId);

    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId
              AND (:status IS NULL OR u.status = :status)
              AND (:query IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<User> searchByTenant(
            @Param("tenantId") String tenantId,
            @Param("query") String query,
            @Param("status") UserStatus status,
            Pageable pageable);
}
