package com.rafaga.backend.identity.application.service;

import java.util.UUID;

/** Public authorization data required by customer-facing modules. */
public record CustomerAccess(UUID userId) {
}
