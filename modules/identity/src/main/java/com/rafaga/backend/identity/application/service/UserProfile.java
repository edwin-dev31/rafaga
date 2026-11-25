package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.List;
import java.util.UUID;

public record UserProfile(UUID id, String username, String email, UserStatus status, List<String> roles) {
    public UserProfile(UUID id, String username, String email, UserStatus status) {
        this(id, username, email, status, List.of());
    }

    public UserProfile {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    static UserProfile from(User user) {
        return from(user, List.of());
    }

    static UserProfile from(User user, List<String> roles) {
        return new UserProfile(user.getId(), user.getUsername(), user.getEmail(), user.getStatus(), roles);
    }
}
