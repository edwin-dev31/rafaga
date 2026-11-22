package com.rafaga.backend.tenant.application.port;

/** Outbound health probe for verifying a deployment target before routing tenant traffic. */
public interface DeploymentTargetHealthProbe {
    boolean isHealthy(String protocol, String privateOrigin, String healthPath);
}
