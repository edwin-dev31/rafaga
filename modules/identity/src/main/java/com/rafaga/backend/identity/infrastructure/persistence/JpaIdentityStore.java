package com.rafaga.backend.identity.infrastructure.persistence;

import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        return userRepository.countByTenantIdAndStatus(tenantId, UserStatus.ACTIVE);
    }

    @Override
    public Page<User> searchUsers(String tenantId, String query, UserStatus status, Pageable pageable) {
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate byUsername = cb.like(cb.lower(root.get("username")), pattern);
                Predicate byEmail = cb.like(cb.lower(root.get("email")), pattern);
                predicates.add(cb.or(byUsername, byEmail));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, pageable);
    }
}
