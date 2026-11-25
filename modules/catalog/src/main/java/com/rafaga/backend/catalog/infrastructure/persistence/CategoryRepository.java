package com.rafaga.backend.catalog.infrastructure.persistence;

import com.rafaga.backend.catalog.domain.model.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByIdAndTenantId(UUID id, String tenantId);
    Optional<Category> findBySlugAndTenantId(String slug, String tenantId);
    List<Category> findAllByTenantIdOrderByDisplayOrderAscNameAsc(String tenantId);
    boolean existsByTenantIdAndParent_Id(String tenantId, UUID parentId);
}
