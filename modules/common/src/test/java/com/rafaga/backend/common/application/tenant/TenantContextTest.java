package com.rafaga.backend.common.application.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void storesAndClearsTheCurrentTenant() {
        TenantContext.setCurrentTenantId("tenant-a");

        assertThat(TenantContext.isSet()).isTrue();
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-a");

        TenantContext.clear();

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void rejectsAccessWhenNoTenantIsSet() {
        assertThatThrownBy(TenantContext::getCurrentTenantId)
                .isInstanceOf(TenantContextNotSetException.class);
    }
}
