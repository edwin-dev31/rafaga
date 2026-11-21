package com.rafaga.backend.tenant.domain.event;

/** Published transactionally so the persisted Modulith event log can drive directory consumers. */
public record TenantDirectoryChangedEvent(long version, String hostname) {
}
