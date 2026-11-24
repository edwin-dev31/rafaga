package com.rafaga.backend.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    public Customer(String tenantId, String fullName, String email, String phone, String notes, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        updateDetails(fullName, email, phone, notes, now);
    }

    public void updateDetails(String fullName, String email, String phone, String notes, Instant now) {
        String normalizedName = normalizeOptional(fullName);
        if (normalizedName == null) {
            throw new IllegalArgumentException("Customer full name is required");
        }
        this.fullName = normalizedName;
        this.email = normalizeEmail(email);
        this.phone = normalizeOptional(phone);
        this.notes = normalizeOptional(notes);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void mergeMissingContact(String fullName, String email, String phone, Instant now) {
        String normalizedName = normalizeOptional(fullName);
        if (normalizedName != null && (this.fullName == null || this.fullName.isBlank())) {
            this.fullName = normalizedName;
        }
        String normalizedEmail = normalizeEmail(email);
        if (this.email == null && normalizedEmail != null) {
            this.email = normalizedEmail;
        }
        String normalizedPhone = normalizeOptional(phone);
        if (this.phone == null && normalizedPhone != null) {
            this.phone = normalizedPhone;
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public static String normalizeEmail(String email) {
        String trimmed = normalizeOptional(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
