package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.tenant.application.port.DatabaseConnectionDetails;
import com.rafaga.backend.tenant.application.port.TenantDatabaseCredentialsProvider;
import com.rafaga.backend.tenant.application.port.TenantRegistry;
import com.rafaga.backend.tenant.domain.model.DatabaseConfig;
import com.rafaga.backend.tenant.domain.model.DeploymentMode;
import com.rafaga.backend.tenant.domain.model.TenantConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/** Routes shared requests to one pool and lazily creates a dedicated pool when a tenant needs one. */
public class TenantAwareRoutingDataSource extends AbstractRoutingDataSource implements AutoCloseable {
    public static final String SYSTEM_KEY = "system";
    public static final String SHARED_KEY = "shared";

    private final TenantRegistry tenantRegistry;
    private final TenantDatabaseCredentialsProvider credentialsProvider;
    private final HikariDataSource sharedDataSource;
    private final Map<String, HikariDataSource> dedicatedDataSources = new ConcurrentHashMap<>();

    public TenantAwareRoutingDataSource(
            TenantRegistry tenantRegistry,
            TenantDatabaseCredentialsProvider credentialsProvider,
            HikariDataSource sharedDataSource) {
        this.tenantRegistry = tenantRegistry;
        this.credentialsProvider = credentialsProvider;
        this.sharedDataSource = sharedDataSource;
        Map<Object, Object> initialDataSources = new HashMap<>();
        initialDataSources.put(SYSTEM_KEY, sharedDataSource);
        initialDataSources.put(SHARED_KEY, sharedDataSource);
        setDefaultTargetDataSource(sharedDataSource);
        setTargetDataSources(initialDataSources);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        if (!TenantContext.isSet()) {
            return SYSTEM_KEY;
        }
        TenantConfig tenant = tenantRegistry.findById(TenantContext.getCurrentTenantId())
                .orElseThrow(() -> new IllegalStateException("Current tenant is not registered"));
        return tenant.deploymentMode() == DeploymentMode.DEDICATED_DB
                ? dedicatedKey(tenant.tenantId()) : SHARED_KEY;
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Object lookupKey = determineCurrentLookupKey();
        if (SYSTEM_KEY.equals(lookupKey) || SHARED_KEY.equals(lookupKey)) {
            return sharedDataSource;
        }
        String tenantId = TenantContext.getCurrentTenantId();
        TenantConfig tenant = tenantRegistry.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Current tenant is not registered"));
        return dedicatedDataSources.computeIfAbsent((String) lookupKey, ignored -> {
            HikariDataSource dataSource = createDedicatedDataSource(tenant);
            Map<Object, Object> targetDataSources = new HashMap<>();
            targetDataSources.put(SYSTEM_KEY, sharedDataSource);
            targetDataSources.put(SHARED_KEY, sharedDataSource);
            targetDataSources.putAll(dedicatedDataSources);
            targetDataSources.put(lookupKey, dataSource);
            setTargetDataSources(targetDataSources);
            afterPropertiesSet();
            return dataSource;
        });
    }

    public Map<String, HikariDataSource> dedicatedDataSources() {
        return Map.copyOf(dedicatedDataSources);
    }

    public DataSource getDataSourceForCurrentTenant() {
        return determineTargetDataSource();
    }

    static String dedicatedKey(String tenantId) {
        return "tenant-" + tenantId;
    }

    private HikariDataSource createDedicatedDataSource(TenantConfig tenant) {
        DatabaseConfig databaseConfig = tenant.databaseConfig();
        if (databaseConfig == null) {
            throw new IllegalStateException("Dedicated tenant has no database secret reference");
        }
        DatabaseConnectionDetails details = credentialsProvider.resolve(databaseConfig);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(details.jdbcUrl());
        config.setUsername(details.username());
        config.setPassword(details.password());
        config.setPoolName("RafagaTenant-" + tenant.tenantId());
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        return new HikariDataSource(config);
    }

    @Override
    public void close() {
        dedicatedDataSources.values().forEach(HikariDataSource::close);
        sharedDataSource.close();
    }
}
