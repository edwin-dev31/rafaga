package com.rafaga.backend.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantDatabaseMigratorIdentifierTest {

    @Test
    void quotesValidPostgresIdentifiersAndRejectsUnsafeOnes() {
        assertThat(TenantDatabaseMigrator.quotedIdentifier("rafaga_runtime")).isEqualTo("\"rafaga_runtime\"");
        assertThat(TenantDatabaseMigrator.quotedIdentifier("product_variants")).isEqualTo("\"product_variants\"");

        assertThatThrownBy(() -> TenantDatabaseMigrator.quotedIdentifier(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDatabaseMigrator.quotedIdentifier(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDatabaseMigrator.quotedIdentifier("role\"; DROP TABLE users; --"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDatabaseMigrator.quotedIdentifier("123invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantDatabaseMigrator.quotedIdentifier("a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
