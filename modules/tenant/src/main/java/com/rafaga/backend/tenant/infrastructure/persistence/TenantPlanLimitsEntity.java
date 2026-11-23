package com.rafaga.backend.tenant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_plan_limits")
public class TenantPlanLimitsEntity {
    @Id
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "plan_id", nullable = false, length = 100)
    private String planId;

    @Column(name = "max_products", nullable = false)
    private int maxProducts;

    @Column(name = "max_assets", nullable = false)
    private long maxAssets;

    @Column(name = "max_storage_bytes", nullable = false)
    private long maxStorageBytes;

    @Column(name = "max_admin_users", nullable = false)
    private int maxAdminUsers;

    @Column(name = "max_domains", nullable = false)
    private int maxDomains;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantPlanLimitsEntity() {
    }

    public TenantPlanLimitsEntity(
            String tenantId,
            String planId,
            int maxProducts,
            long maxAssets,
            long maxStorageBytes,
            int maxAdminUsers,
            int maxDomains,
            Instant updatedAt) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.maxProducts = maxProducts;
        this.maxAssets = maxAssets;
        this.maxStorageBytes = maxStorageBytes;
        this.maxAdminUsers = maxAdminUsers;
        this.maxDomains = maxDomains;
        this.updatedAt = updatedAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public int getMaxProducts() {
        return maxProducts;
    }

    public void setMaxProducts(int maxProducts) {
        this.maxProducts = maxProducts;
    }

    public long getMaxAssets() {
        return maxAssets;
    }

    public void setMaxAssets(long maxAssets) {
        this.maxAssets = maxAssets;
    }

    public long getMaxStorageBytes() {
        return maxStorageBytes;
    }

    public void setMaxStorageBytes(long maxStorageBytes) {
        this.maxStorageBytes = maxStorageBytes;
    }

    public int getMaxAdminUsers() {
        return maxAdminUsers;
    }

    public void setMaxAdminUsers(int maxAdminUsers) {
        this.maxAdminUsers = maxAdminUsers;
    }

    public int getMaxDomains() {
        return maxDomains;
    }

    public void setMaxDomains(int maxDomains) {
        this.maxDomains = maxDomains;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
