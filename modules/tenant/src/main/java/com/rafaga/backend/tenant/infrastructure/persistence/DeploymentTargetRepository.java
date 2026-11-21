package com.rafaga.backend.tenant.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeploymentTargetRepository extends JpaRepository<DeploymentTargetEntity, String> { }
