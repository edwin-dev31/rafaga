package com.rafaga.backend.catalog.infrastructure.persistence;

import com.rafaga.backend.catalog.domain.model.Product;
import com.rafaga.backend.catalog.domain.model.ProductStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndTenantId(UUID id, String tenantId);
    Optional<Product> findBySkuAndTenantId(String sku, String tenantId);
    long countByTenantId(String tenantId);
    Page<Product> findAllByTenantId(String tenantId, Pageable pageable);
    Page<Product> findAllByTenantIdAndStatus(String tenantId, ProductStatus status, Pageable pageable);
    Optional<Product> findByIdAndTenantIdAndStatus(UUID id, String tenantId, ProductStatus status);
}
