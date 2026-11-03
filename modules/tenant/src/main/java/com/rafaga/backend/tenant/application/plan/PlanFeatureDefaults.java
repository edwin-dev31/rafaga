package com.rafaga.backend.tenant.application.plan;

import java.util.Map;

/** Plan defaults are deliberately small and may be replaced by a billing-plan adapter later. */
public interface PlanFeatureDefaults {
    Map<String, Boolean> featuresFor(String planId);
}
