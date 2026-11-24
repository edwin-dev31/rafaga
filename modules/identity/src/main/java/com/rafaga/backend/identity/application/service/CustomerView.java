package com.rafaga.backend.identity.application.service;

import com.rafaga.backend.identity.domain.model.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerView(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String phone,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
    public static CustomerView from(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getUserId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getNotes(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
