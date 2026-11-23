package com.rafaga.backend.identity.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.identity.application.port.AuthenticationPort;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.application.port.UserRegistration;
import com.rafaga.backend.identity.application.security.RegistrationProperties;
import com.rafaga.backend.identity.application.security.RegistrationTokenValidator;
import com.rafaga.backend.tenant.application.PlanLimitsDefaults;
import com.rafaga.backend.tenant.application.TenantLimitExceededException;
import com.rafaga.backend.tenant.application.TenantLimitsQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserServicePlanLimitTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsRegisteringAdminUserWhenTenantReachesMaxAdminUsers() {
        TenantContext.setCurrentTenantId("tenant-1");
        AuthenticationPort auth = mock(AuthenticationPort.class);
        IdentityStore store = mock(IdentityStore.class);
        TenantLimitsQuery limitsQuery = mock(TenantLimitsQuery.class);
        RegistrationProperties props = new RegistrationProperties();
        props.setRegistrationToken("valid-token");

        when(store.countUsers("tenant-1")).thenReturn(1L);
        when(limitsQuery.findLimits("tenant-1"))
                .thenReturn(Optional.of(PlanLimitsDefaults.forPlan("tenant-1", "starter", NOW)));

        UserService service = new UserService(
                auth, store, new RegistrationTokenValidator(props), limitsQuery, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.registerUser(
                "valid-token",
                new UserRegistration("admin2", "Admin", "Two", "admin2@example.test", "StrongPass123!")))
                .isInstanceOf(TenantLimitExceededException.class)
                .hasMessageContaining("adminUsers");
        verify(auth, never()).registerUser(any(), any());
    }
}
