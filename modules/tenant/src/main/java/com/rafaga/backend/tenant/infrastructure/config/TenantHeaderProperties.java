package com.rafaga.backend.tenant.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rafaga.tenant.internal-header")
public class TenantHeaderProperties {
    private boolean enabled;
    private List<String> trustedCidrs = List.of();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getTrustedCidrs() { return trustedCidrs; }
    public void setTrustedCidrs(List<String> trustedCidrs) { this.trustedCidrs = List.copyOf(trustedCidrs); }
}
