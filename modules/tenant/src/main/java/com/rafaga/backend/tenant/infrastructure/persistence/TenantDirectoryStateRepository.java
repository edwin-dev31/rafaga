package com.rafaga.backend.tenant.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface TenantDirectoryStateRepository extends JpaRepository<TenantDirectoryState, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantDirectoryState> findBySingletonId(short singletonId);
}
