package com.weekly.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Registers the tenant RLS transaction listener as a Spring bean.
 *
 * <p>Spring Boot's transaction manager customization auto-detects
 * {@link TransactionExecutionListener} beans and attaches them to the
 * application's transaction manager, so no manual transaction-manager
 * mutation is required here.
 *
 * <p>Guarded by {@code tenant.rls.enabled} so the listener stays off for the
 * H2-based default test profile, but can be explicitly re-enabled for
 * Postgres-backed integration tests that need real RLS coverage.
 */
@Configuration
public class TenantRlsConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "tenant.rls.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public TenantRlsTransactionListener tenantRlsTransactionListener(JdbcOperations jdbcOperations) {
        return new TenantRlsTransactionListener(jdbcOperations);
    }
}
