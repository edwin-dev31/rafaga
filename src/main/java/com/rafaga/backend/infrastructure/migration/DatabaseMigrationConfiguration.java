package com.rafaga.backend.infrastructure.migration;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DatabaseMigrationConfiguration {
    @Bean
    FlywayMigrationStrategy migrateSharedAndDedicatedDatabases(TenantDatabaseMigrator migrator) {
        return flyway -> {
            MigrationLocationCompatibility.includePreviouslyAppliedDevSeed(flyway).migrate();
            migrator.migrateRegistered(flyway.getConfiguration().getDataSource());
        };
    }
}
