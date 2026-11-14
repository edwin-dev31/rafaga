package com.rafaga.backend.tenant.domain.model;

@org.springframework.modulith.NamedInterface("api")
public enum DeploymentMode {
    SHARED_DB, DEDICATED_DB, DEDICATED_INSTANCE
}
