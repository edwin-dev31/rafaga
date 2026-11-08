package com.rafaga.backend.catalog.infrastructure.persistence;

import com.rafaga.backend.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndTenantId(UUID id, String tenantId);
    Optional<Product> findBySkuAndTenantId(String sku, String tenantId);
    Page<Product> findAllByTenantId(String tenantId, Pageable pageable);
}
