package com.weekly.config;

import com.weekly.auth.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

import java.util.UUID;

/**
 * Executes {@code SET LOCAL app.current_org_id = '...'} at the start of
 * each transaction so that Postgres Row-Level Security policies filter
 * rows to the authenticated tenant.
 *
 * <p>The tenant org ID is sourced from the {@link UserPrincipal} stored in
 * Spring Security's {@link SecurityContextHolder}. The principal is populated
 * by {@link com.weekly.auth.JwtAuthenticationFilter} from the validated JWT
 * or dev-auth headers before any service method is invoked.
 *
 * <p>{@code SET LOCAL} scopes the variable to the current transaction;
 * it is automatically cleared on COMMIT or ROLLBACK, preventing context
 * leakage across pooled connections.
 *
 * <p>Registered on the {@link org.springframework.orm.jpa.JpaTransactionManager}
 * by {@link TenantRlsConfiguration}. Disabled in the {@code test} profile
 * because H2 does not support Postgres GUC variables.
 */
public class TenantRlsTransactionListener implements TransactionExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TenantRlsTransactionListener.class);

    private final JdbcOperations jdbcOperations;

    public TenantRlsTransactionListener(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    /**
     * Called by Spring after a new transaction is opened and the connection
     * is bound to the current thread. Executes {@code SET LOCAL} so that
     * RLS policies take effect for all queries in this transaction.
     *
     * @param transaction  the transaction that was just started
     * @param beginFailure non-null if the transaction failed to open
     */
    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure != null) {
            return;
        }
        UUID orgId = resolveOrgId();
        if (orgId == null) {
            LOG.debug("No authenticated principal; skipping RLS initialisation for transaction");
            return;
        }
        String sql = TenantConnectionInterceptor.setTenantSql(orgId);
        jdbcOperations.execute(sql);
        LOG.trace("RLS tenant context set for org {}", orgId);
    }

    private UUID resolveOrgId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return null;
        }
        return userPrincipal.orgId();
    }
}
