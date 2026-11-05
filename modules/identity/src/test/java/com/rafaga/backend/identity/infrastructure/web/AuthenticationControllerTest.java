package com.rafaga.backend.identity.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rafaga.backend.identity.application.exception.InvalidRegistrationTokenException;
import com.rafaga.backend.identity.application.port.AuthenticationTokens;
import com.rafaga.backend.identity.application.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticationControllerTest {
    @Test
    void returnsKeycloakTokensFromTheBackendLoginEndpoint() throws Exception {
        UserService service = mock(UserService.class);
        when(service.authenticateUser("person", "secret"))
                .thenReturn(new AuthenticationTokens("access", "refresh", "Bearer", 300));

        mockMvc(service).perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"person\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    void rejectsRegistrationWhenTheGlobalTokenIsInvalid() throws Exception {
        UserService service = mock(UserService.class);
        when(service.registerUser(org.mockito.ArgumentMatchers.eq("invalid"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new InvalidRegistrationTokenException());

        mockMvc(service).perform(post("/api/public/auth/register")
                        .header("X-Registration-Token", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"person\",\"firstName\":\"Person\",\"lastName\":\"Example\",\"email\":\"person@example.test\",\"password\":\"secret\"}"))
                .andExpect(status().isForbidden());
    }

    private MockMvc mockMvc(UserService service) {
        return MockMvcBuilders.standaloneSetup(new AuthenticationController(service))
                .setControllerAdvice(new IdentityExceptionHandler())
                .build();
    }
}
