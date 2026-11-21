package com.rafaga.backend.tenant.domain.model;

import java.util.List;

/** Versioned snapshot or delta exposed only to the internal gateway directory client. */
@org.springframework.modulith.NamedInterface("api")
public record TenantDirectorySnapshot(
        long directoryVersion,
        boolean delta,
        List<TenantDirectoryEntry> activeEntries,
        List<String> removedHostnames) {
}
