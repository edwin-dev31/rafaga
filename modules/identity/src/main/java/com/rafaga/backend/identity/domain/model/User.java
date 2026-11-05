package com.rafaga.backend.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        indexes = @Index(name = "idx_users_tenant_id", columnList = "tenant_id"),
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_tenant_keycloak",
                        columnNames = {"tenant_id", "keycloak_id"}),
                @UniqueConstraint(
                        name = "uk_users_tenant_username",
                        columnNames = {"tenant_id", "username"})
        })
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "keycloak_id", nullable = false, length = 255, updatable = false)
    private String keycloakId;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
    }

    public User(String tenantId, String keycloakId, String username, String email, Instant now) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.keycloakId = Objects.requireNonNull(keycloakId, "keycloakId must not be null");
        updateProfile(username, email, now);
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
    }

    public void updateProfile(String username, String email, Instant now) {
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.lastLoginAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void assignRole(Role role) {
        if (!tenantId.equals(role.getTenantId())) {
            throw new IllegalArgumentException("A role can only be assigned within the same tenant");
        }
        roles.add(role);
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
