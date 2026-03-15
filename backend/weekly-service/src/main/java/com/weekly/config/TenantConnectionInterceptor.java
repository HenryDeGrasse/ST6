package com.weekly.config;

import java.util.UUID;

/**
 * Provides the SQL template for setting the Postgres session variable
 * {@code app.current_org_id} at the start of each transaction for
 * Row-Level Security enforcement.
 *
 * <p>The actual JDBC integration is performed by
 * {@link TenantRlsTransactionListener}, which is registered as a Spring bean
 * by {@link TenantRlsConfiguration} and auto-attached to the JPA transaction
 * manager by Spring Boot's {@code ExecutionListenersTransactionManagerCustomizer}.
 * On every new transaction, {@link TenantRlsTransactionListener#afterBegin}
 * calls {@link #setTenantSql} and executes the result via {@code JdbcOperations}.
 */
public final class TenantConnectionInterceptor {

    private TenantConnectionInterceptor() {}

    /**
     * Returns the SQL statement to set the tenant context on a connection.
     *
     * @param orgId the organization ID from the JWT
     * @return SQL SET LOCAL statement
     */
    public static String setTenantSql(UUID orgId) {
        // Using SET LOCAL so the setting is transaction-scoped and
        // automatically reverted when the transaction ends, preventing
        // context leakage across pooled connections.
        return "SET LOCAL app.current_org_id = '" + orgId.toString() + "'";
    }
}
