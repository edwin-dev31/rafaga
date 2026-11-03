package com.rafaga.backend.tenant.domain.event;

import com.rafaga.backend.tenant.domain.model.TenantStatus;

public record TenantStatusChangedEvent(String tenantId, TenantStatus oldStatus, TenantStatus newStatus) {
}
