package com.rafaga.backend.identity.application.port;

import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IdentityStore {
    Optional<User> findUserById(String tenantId, UUID userId);

    Optional<User> findUserByKeycloakId(String tenantId, String keycloakId);

    User saveUser(User user);

    default long countUsers(String tenantId) {
        return 0L;
    }

    default Page<User> searchUsers(String tenantId, String query, UserStatus status, Pageable pageable) {
        return Page.empty(pageable);
    }
}
