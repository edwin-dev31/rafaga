package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies the current tenant to a PostgreSQL connection and clears it before the pool reuses it. */
final class TenantConnectionCustomizer {
    private static final String SET_TENANT_SQL = "SELECT set_config('app.current_tenant', ?, false)";

    Connection customize(Connection connection) throws SQLException {
        try {
            setTenant(connection, TenantContext.isSet() ? TenantContext.getCurrentTenantId() : "");
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close") && method.getParameterCount() == 0) {
                        if (closed.compareAndSet(false, true)) {
                            try {
                                clearTenant(connection);
                            } finally {
                                connection.close();
                            }
                        }
                        return null;
                    }
                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private void clearTenant(Connection connection) throws SQLException {
        setTenant(connection, "");
    }

    private void setTenant(Connection connection, String tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_SQL)) {
            statement.setString(1, tenantId);
            statement.execute();
        }
    }
}
