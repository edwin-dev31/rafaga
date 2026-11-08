package com.rafaga.backend.infrastructure.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;

/** Keeps historical V4 seed migrations resolvable without applying that seed to new databases. */
final class MigrationLocationCompatibility {
    private static final Location LEGACY_DEV_SEED = new Location("classpath:db/dev-migration");

    private MigrationLocationCompatibility() {
    }

    static Flyway includePreviouslyAppliedDevSeed(Flyway flyway) {
        Location[] locations = flyway.getConfiguration().getLocations();
        if (Arrays.asList(locations).contains(LEGACY_DEV_SEED) || !hasAppliedDevSeed(flyway)) {
            return flyway;
        }
        Location[] compatibleLocations = Arrays.copyOf(locations, locations.length + 1);
        compatibleLocations[locations.length] = LEGACY_DEV_SEED;
        return Flyway.configure()
                .configuration(flyway.getConfiguration())
                .locations(compatibleLocations)
                .load();
    }

    private static boolean hasAppliedDevSeed(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet table = statement.executeQuery("SELECT to_regclass('flyway_schema_history')")) {
                table.next();
                if (table.getString(1) == null) {
                    return false;
                }
            }
            try (ResultSet migration = statement.executeQuery(
                    "SELECT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '4' "
                            + "AND description = 'seed demo tenant' AND success)")) {
                migration.next();
                return migration.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot inspect Flyway history for legacy development seed", exception);
        }
    }
}
