package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.exception.FeatureDisabledException;
import com.rafaga.backend.tenant.application.service.FeatureFlagService;
import com.rafaga.backend.tenant.application.RequiresFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
class FeatureFlagInterceptor implements HandlerInterceptor {
    private final FeatureFlagService featureFlags;

    FeatureFlagInterceptor(FeatureFlagService featureFlags) {
        this.featureFlags = featureFlags;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresFeature requirement = handlerMethod.getMethodAnnotation(RequiresFeature.class);
        if (requirement == null) {
            requirement = handlerMethod.getBeanType().getAnnotation(RequiresFeature.class);
        }
        if (requirement != null && !featureFlags.isFeatureEnabled(requirement.value())) {
            throw new FeatureDisabledException(requirement.value());
        }
        return true;
    }
}
