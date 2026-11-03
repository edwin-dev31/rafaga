package com.rafaga.backend.tenant.application.plan;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class DefaultPlanFeatureDefaults implements PlanFeatureDefaults {
    @Override
    public Map<String, Boolean> featuresFor(String planId) {
        return switch (planId.toLowerCase(Locale.ROOT)) {
            case "pro", "professional" -> Map.of(
                    "catalog", true,
                    "inventory", true,
                    "orders", true,
                    "analytics", true);
            case "enterprise" -> Map.of(
                    "catalog", true,
                    "inventory", true,
                    "orders", true,
                    "analytics", true,
                    "custom-domain", true);
            default -> Map.of(
                    "catalog", true,
                    "inventory", true,
                    "orders", true,
                    "analytics", false);
        };
    }
}
