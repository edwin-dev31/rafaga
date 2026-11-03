package com.rafaga.backend.tenant.infrastructure.config;

import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({TenantRegistry.class, TenantDatabaseCredentialsProvider.class})
@EnableConfigurationProperties(SharedDataSourceProperties.class)
public class DataSourceRoutingConfiguration {
    @Bean(destroyMethod = "close")
    @Primary
    TenantAwareRoutingDataSource tenantAwareRoutingDataSource(
            @Lazy TenantRegistry tenantRegistry,
            TenantDatabaseCredentialsProvider credentialsProvider,
            SharedDataSourceProperties sharedProperties) {
        HikariDataSource sharedDataSource = sharedProperties.createDataSource();
        return new TenantAwareRoutingDataSource(tenantRegistry, credentialsProvider, sharedDataSource);
    }
}
