package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.identity.application.port.IdentityStore;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public identity lookup API that prevents other modules from accessing identity entities. */
@Service
public class IdentityAccessService {
    private final IdentityStore identityStore;

    public IdentityAccessService(IdentityStore identityStore) {
        this.identityStore = identityStore;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerAccess> findCustomerAccess(String tenantId, String keycloakId) {
        return identityStore.findUserByKeycloakId(tenantId, keycloakId)
                .map(user -> new CustomerAccess(user.getId()));
    }
}
