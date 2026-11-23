package com.rafaga.backend.identity.infrastructure.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.identity.application.exception.IdentityProviderUnavailableException;
import com.rafaga.backend.identity.application.exception.IdentityResourceNotFoundException;
import com.rafaga.backend.identity.application.exception.InvalidCredentialsException;
import com.rafaga.backend.identity.application.exception.PasswordPolicyViolationException;
import com.rafaga.backend.identity.application.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {
    @Test
    void changesCurrentUserPasswordAndReturnsNoContent() throws Exception {
        UserService service = mock(UserService.class);

        mockMvc(service).perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPass123!\",\"newPassword\":\"NewStrongPass456!\"}"))
                .andExpect(status().isNoContent());

        verify(service).changeCurrentUserPassword("CurrentPass123!", "NewStrongPass456!");
    }

    @Test
    void returnsUnauthorizedWhenCurrentPasswordIsWrong() throws Exception {
        UserService service = mock(UserService.class);
        doThrow(new InvalidCredentialsException())
                .when(service).changeCurrentUserPassword("WrongPass123!", "NewStrongPass456!");

        mockMvc(service).perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPass123!\",\"newPassword\":\"NewStrongPass456!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsBadRequestWhenNewPasswordFailsBeanValidation() throws Exception {
        UserService service = mock(UserService.class);

        mockMvc(service).perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPass123!\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void returnsBadRequestWhenPasswordViolatesPolicy() throws Exception {
        UserService service = mock(UserService.class);
        doThrow(new PasswordPolicyViolationException("New password must be different from current password"))
                .when(service).changeCurrentUserPassword("SamePass123!", "SamePass123!");

        mockMvc(service).perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"SamePass123!\",\"newPassword\":\"SamePass123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from current password"));
    }

    @Test
    void resetsUserPasswordAsAdminAndDefaultsTemporaryToFalse() throws Exception {
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();

        mockMvc(service).perform(put("/api/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"AdminResetPass123!\"}"))
                .andExpect(status().isNoContent());

        verify(service).resetUserPassword(userId, "AdminResetPass123!", false);
    }

    @Test
    void resetsUserPasswordWithTemporaryFlagWhenProvided() throws Exception {
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();

        mockMvc(service).perform(put("/api/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"AdminResetPass123!\",\"temporary\":true}"))
                .andExpect(status().isNoContent());

        verify(service).resetUserPassword(userId, "AdminResetPass123!", true);
    }

    @Test
    void returnsNotFoundWhenAdminResetsPasswordForMissingOrCrossTenantUser() throws Exception {
        UserService service = mock(UserService.class);
        UUID userId = UUID.randomUUID();
        doThrow(new IdentityResourceNotFoundException("User", userId))
                .when(service).resetUserPassword(userId, "AdminResetPass123!", false);

        mockMvc(service).perform(put("/api/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"AdminResetPass123!\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsServiceUnavailableWhenKeycloakIsDownDuringPasswordUpdate() throws Exception {
        UserService service = mock(UserService.class);
        doThrow(new IdentityProviderUnavailableException("Keycloak timeout"))
                .when(service).changeCurrentUserPassword("CurrentPass123!", "NewStrongPass456!");

        mockMvc(service).perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"CurrentPass123!\",\"newPassword\":\"NewStrongPass456!\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Authentication service is temporarily unavailable"));
    }

    private MockMvc mockMvc(UserService service) {
        return MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }
}
