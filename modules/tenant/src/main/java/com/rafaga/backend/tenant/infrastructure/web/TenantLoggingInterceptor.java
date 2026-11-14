package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.common.application.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Makes the resolved tenant available to structured request and business-operation logs. */
@Component
public class TenantLoggingInterceptor implements HandlerInterceptor {
    public static final String TENANT_ID = "tenantId";
    public static final String SYSTEM_TENANT = "SYSTEM";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put(TENANT_ID, TenantContext.isSet() ? TenantContext.getCurrentTenantId() : SYSTEM_TENANT);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(TENANT_ID);
    }
}
