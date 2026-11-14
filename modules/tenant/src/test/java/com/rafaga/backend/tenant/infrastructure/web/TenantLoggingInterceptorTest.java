package com.rafaga.backend.tenant.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.common.application.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantLoggingInterceptorTest {
    private final TenantLoggingInterceptor interceptor = new TenantLoggingInterceptor();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void addsTheResolvedTenantToMdcForBusinessOperations() {
        TenantContext.setCurrentTenantId("tenant-a");

        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(MDC.get(TenantLoggingInterceptor.TENANT_ID)).isEqualTo("tenant-a");
    }

    @Test
    void marksOperationsWithoutTenantContextAsSystemAndCleansUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        assertThat(MDC.get(TenantLoggingInterceptor.TENANT_ID)).isEqualTo(TenantLoggingInterceptor.SYSTEM_TENANT);
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get(TenantLoggingInterceptor.TENANT_ID)).isNull();
    }
}
