package com.rafaga.backend.identity.infrastructure.web;

import com.rafaga.backend.identity.application.service.UserProfile;
import com.rafaga.backend.identity.application.service.UserService;
import com.rafaga.backend.identity.domain.model.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeCurrentUserPassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(request.currentPassword(), request.newPassword());
    }

    @PutMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetUserPassword(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetUserPassword(userId, request.newPassword(), Boolean.TRUE.equals(request.temporary()));
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 128) String newPassword,
            Boolean temporary) {
    }

    public record UserResponse(UUID id, String username, String email, UserStatus status, List<String> roles) {
        static UserResponse from(UserProfile profile) {
            return new UserResponse(
                    profile.id(), profile.username(), profile.email(), profile.status(), profile.roles());
        }
    }
}
