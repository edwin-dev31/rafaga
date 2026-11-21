package com.rafaga.backend.tenant.domain.model;

/** Safe routing data exposed only to the internal gateway directory client. */
@org.springframework.modulith.NamedInterface("api")
public record TenantDirectoryEntry(
        String hostname,
        String tenantId,
        String frontendOrigin,
        DeploymentTargetType targetType,
        String targetId,
        long directoryVersion) {
}
