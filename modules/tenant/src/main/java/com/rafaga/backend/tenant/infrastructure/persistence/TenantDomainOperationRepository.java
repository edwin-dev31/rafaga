package com.rafaga.backend.tenant.infrastructure.persistence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TenantDomainOperationRepository extends JpaRepository<TenantDomainOperation,String> {
    Optional<TenantDomainOperation> findByTenantIdAndDomainAndTypeAndKey(String tenantId, String domain, String type, String key);
}
