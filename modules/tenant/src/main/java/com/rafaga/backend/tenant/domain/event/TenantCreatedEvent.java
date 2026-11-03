package com.rafaga.backend.tenant.domain.event;

import com.rafaga.backend.tenant.domain.model.DeploymentMode;

public record TenantCreatedEvent(String tenantId, String name, DeploymentMode deploymentMode) {
}
