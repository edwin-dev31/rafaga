package com.rafaga.backend.tenant.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class TenantLoggingWebConfiguration implements WebMvcConfigurer {
    private final TenantLoggingInterceptor tenantLoggingInterceptor;
    private final FeatureFlagInterceptor featureFlagInterceptor;

    TenantLoggingWebConfiguration(TenantLoggingInterceptor tenantLoggingInterceptor,
            FeatureFlagInterceptor featureFlagInterceptor) {
        this.tenantLoggingInterceptor = tenantLoggingInterceptor;
        this.featureFlagInterceptor = featureFlagInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantLoggingInterceptor);
        registry.addInterceptor(featureFlagInterceptor).addPathPatterns("/api/**");
    }
}
