package com.rafaga.backend.tenant.infrastructure.config;

import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.application.port.DedicatedDatabaseMigration;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SharedDataSourceProperties.class)
public class DataSourceRoutingConfiguration {
    @Bean(destroyMethod = "close")
    @Primary
    TenantAwareRoutingDataSource tenantAwareRoutingDataSource(
            @Lazy TenantRegistry tenantRegistry,
            ObjectProvider<TenantDatabaseCredentialsProvider> credentialsProvider,
            DedicatedDatabaseMigration databaseMigration,
            SharedDataSourceProperties sharedProperties) {
        HikariDataSource sharedDataSource = sharedProperties.createDataSource();
        TenantDatabaseCredentialsProvider provider = credentialsProvider.getIfAvailable();
        if (provider == null) {
            provider = config -> {
                throw new IllegalStateException("Dedicated database credentials provider is not configured");
            };
        }
        return new TenantAwareRoutingDataSource(tenantRegistry, provider, databaseMigration, sharedDataSource);
    }
}
