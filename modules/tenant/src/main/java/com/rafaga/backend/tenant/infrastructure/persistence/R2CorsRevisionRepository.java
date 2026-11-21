package com.rafaga.backend.tenant.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface R2CorsRevisionRepository extends JpaRepository<R2CorsRevisionEntity, Long> {
    Optional<R2CorsRevisionEntity> findTopByOrderByVersionDesc();

    List<R2CorsRevisionEntity> findTop20ByOrderByVersionDesc();
}
