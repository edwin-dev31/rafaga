package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.service.UserProfile;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.identity.domain.model.UserStatus;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable("userId") UUID userId) {
        return UserResponse.from(userService.getUserById(userId));
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        return UserResponse.from(userService.getCurrentUser());
    }

    public record UserResponse(UUID id, String username, String email, UserStatus status) {
        static UserResponse from(UserProfile profile) {
            return new UserResponse(profile.id(), profile.username(), profile.email(), profile.status());
        }
    }
}
