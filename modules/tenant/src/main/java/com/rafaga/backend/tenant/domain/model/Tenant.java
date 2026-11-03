package com.rafaga.backend.tenant.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_domains", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "domain", nullable = false, length = 253)
    private List<String> domains = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_mode", nullable = false, length = 24)
    private DeploymentMode deploymentMode;

    @Column(name = "database_config_id", length = 200)
    private String databaseConfigId;

    @Column(name = "plan_id", nullable = false, length = 100)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(
            String id,
            String name,
            List<String> domains,
            DeploymentMode deploymentMode,
            String databaseConfigId,
            String planId,
            TenantStatus status,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        update(name, domains, deploymentMode, databaseConfigId, planId, status, now);
        this.createdAt = now;
    }

    public void update(
            String name,
            List<String> domains,
            DeploymentMode deploymentMode,
            String databaseConfigId,
            String planId,
            TenantStatus status,
            Instant now) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.domains = new ArrayList<>(Objects.requireNonNull(domains, "domains must not be null").stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT)).toList());
        if (this.domains.isEmpty()) {
            throw new IllegalArgumentException("Tenant must have at least one domain");
        }
        this.deploymentMode = Objects.requireNonNull(deploymentMode, "deploymentMode must not be null");
        this.databaseConfigId = databaseConfigId;
        this.planId = Objects.requireNonNull(planId, "planId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getDomains() {
        return List.copyOf(domains);
    }

    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public String getDatabaseConfigId() {
        return databaseConfigId;
    }

    public String getPlanId() {
        return planId;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
