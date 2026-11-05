package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.UUID;

public record UserProfile(UUID id, String username, String email, UserStatus status) {
    static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getEmail(), user.getStatus());
    }
}
