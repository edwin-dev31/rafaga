package com.rafaga.backend.tenant.infrastructure.persistence;

import java.io.Serializable;

public record TenantDomainControlId(String tenantId, String domain) implements Serializable {
}
