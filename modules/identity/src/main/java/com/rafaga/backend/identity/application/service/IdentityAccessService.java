package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.identity.application.port.CustomerStore;
import com.rafaga.backend.identity.application.port.IdentityStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public identity lookup API that prevents other modules from accessing identity entities. */
@Service
public class IdentityAccessService {
    private final IdentityStore identityStore;
    private final CustomerStore customerStore;

    public IdentityAccessService(IdentityStore identityStore) {
        this(identityStore, (CustomerStore) null);
    }

    @Autowired
    public IdentityAccessService(
            IdentityStore identityStore,
            ObjectProvider<CustomerStore> customerStoreProvider) {
        this(identityStore, customerStoreProvider.getIfAvailable());
    }

    IdentityAccessService(IdentityStore identityStore, CustomerStore customerStore) {
        this.identityStore = identityStore;
        this.customerStore = customerStore;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerAccess> findCustomerAccess(String tenantId, String keycloakId) {
        return identityStore.findUserByKeycloakId(tenantId, keycloakId)
                .map(user -> new CustomerAccess(user.getId()));
    }

    @Transactional(readOnly = true)
    public boolean belongsToTenant(String tenantId, UUID customerOrUserId) {
        if (tenantId == null || tenantId.isBlank() || customerOrUserId == null) {
            return false;
        }
        return (customerStore != null && customerStore.findById(tenantId, customerOrUserId).isPresent())
                || identityStore.findUserById(tenantId, customerOrUserId).isPresent();
    }
}
