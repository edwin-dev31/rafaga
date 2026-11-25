package com.rafaga.backend.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.identity.application.port.CustomerStore;
import com.rafaga.backend.identity.application.port.IdentityStore;
import com.rafaga.backend.identity.domain.model.Customer;
import com.rafaga.backend.identity.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityAccessServiceTest {

    @Test
    void recognizesDirectoryCustomersAndLocalUsersScopedToSameTenantOnly() {
        IdentityStore identityStore = mock(IdentityStore.class);
        CustomerStore customerStore = mock(CustomerStore.class);
        IdentityAccessService service = new IdentityAccessService(identityStore, customerStore);

        UUID directoryCustomerId = UUID.randomUUID();
        UUID localUserId = UUID.randomUUID();
        UUID foreignCustomerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-25T12:00:00Z");

        when(customerStore.findById("tenant-a", directoryCustomerId))
                .thenReturn(Optional.of(new Customer("tenant-a", "Maria Lopez", "maria@test.co", null, null, now)));
        when(customerStore.findById("tenant-a", localUserId)).thenReturn(Optional.empty());
        when(identityStore.findUserById("tenant-a", localUserId))
                .thenReturn(Optional.of(new User("tenant-a", "kc-1", "mgr", "mgr@test.co", now)));
        when(customerStore.findById("tenant-a", foreignCustomerId)).thenReturn(Optional.empty());
        when(identityStore.findUserById("tenant-a", foreignCustomerId)).thenReturn(Optional.empty());

        assertThat(service.belongsToTenant("tenant-a", directoryCustomerId)).isTrue();
        assertThat(service.belongsToTenant("tenant-a", localUserId)).isTrue();
        assertThat(service.belongsToTenant("tenant-a", foreignCustomerId)).isFalse();
        assertThat(service.belongsToTenant(null, directoryCustomerId)).isFalse();
        assertThat(service.belongsToTenant("tenant-a", null)).isFalse();
    }
}
