package com.rafaga.backend.tenant.infrastructure.cloudflare;

import com.rafaga.backend.tenant.application.port.CloudflareHostnamePort;
import com.rafaga.backend.tenant.application.port.CloudflareProvisioningException;
import com.rafaga.backend.tenant.application.port.CustomHostnameStatus;
import com.rafaga.backend.tenant.application.port.R2CorsPort;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fail-closed fallback adapters when Cloudflare API provisioning is not enabled. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CloudflareProperties.class)
public class DisabledCloudflareAdapters {
    @Bean
    @ConditionalOnMissingBean(CloudflareHostnamePort.class)
    CloudflareHostnamePort disabledCloudflareHostnamePort() {
        return new CloudflareHostnamePort() {
            @Override
            public CustomHostnameStatus createOrGetCustomHostname(String hostname) {
                throw new CloudflareProvisioningException(
                        "CLOUDFLARE_DISABLED", "Cloudflare custom hostname provisioning is disabled", false);
            }

            @Override
            public Optional<CustomHostnameStatus> getCustomHostname(String externalHostnameId, String hostname) {
                throw new CloudflareProvisioningException(
                        "CLOUDFLARE_DISABLED", "Cloudflare custom hostname provisioning is disabled", false);
            }

            @Override
            public void deleteCustomHostname(String externalHostnameId) {
                throw new CloudflareProvisioningException(
                        "CLOUDFLARE_DISABLED", "Cloudflare custom hostname provisioning is disabled", false);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(R2CorsPort.class)
    R2CorsPort disabledR2CorsPort() {
        return (List<String> origins) -> {
            throw new CloudflareProvisioningException(
                    "CLOUDFLARE_DISABLED", "Cloudflare R2 CORS synchronization is disabled", false);
        };
    }
}
