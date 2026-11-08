package com.rafaga.backend.tenant.infrastructure.persistence;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TenantConnectionCustomizerTest {
    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void setsAndClearsTheTenantBeforeReturningTheConnectionToThePool() throws Exception {
        TenantContext.setCurrentTenantId("tenant-a");
        Connection connection = mock(Connection.class);
        PreparedStatement setTenant = mock(PreparedStatement.class);
        PreparedStatement clearTenant = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)"))
                .thenReturn(setTenant, clearTenant);

        Connection customized = new TenantConnectionCustomizer().customize(connection);

        customized.close();

        InOrder calls = inOrder(setTenant, clearTenant, connection);
        calls.verify(setTenant).setString(1, "tenant-a");
        calls.verify(setTenant).execute();
        calls.verify(clearTenant).setString(1, "");
        calls.verify(clearTenant).execute();
        calls.verify(connection).close();
    }
}
