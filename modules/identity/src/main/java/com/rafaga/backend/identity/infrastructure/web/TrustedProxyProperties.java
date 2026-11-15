package com.rafaga.backend.identity.infrastructure.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CIDR ranges from which forwarded headers may be trusted. */
@ConfigurationProperties(prefix = "rafaga.web.trusted-proxy")
public class TrustedProxyProperties {
    private List<String> cidrs = List.of();

    public List<String> getCidrs() {
        return cidrs;
    }

    public void setCidrs(List<String> cidrs) {
        this.cidrs = cidrs == null ? List.of() : List.copyOf(cidrs);
    }
}
