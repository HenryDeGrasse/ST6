package com.weekly.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.weekly.auth.UserPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionExecution;

/**
 * Unit tests for {@link TenantRlsTransactionListener}.
 *
 * <p>Verifies that the correct {@code SET LOCAL} SQL is executed via
 * {@link JdbcOperations} when a transaction begins with an authenticated
 * principal, and that no SQL is executed when the context is absent or the
 * transaction failed to begin.
 */
class TenantRlsTransactionListenerTest {

    private static final UUID ORG_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID orgId) {
        UserPrincipal principal = new UserPrincipal(USER_ID, orgId, Set.of("USER"));
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("USER"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void afterBeginExecutesSetLocalSqlWhenPrincipalIsAuthenticated() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        authenticateAs(ORG_ID);

        TenantRlsTransactionListener listener = new TenantRlsTransactionListener(jdbc);
        listener.afterBegin(mock(TransactionExecution.class), null);

        verify(jdbc).execute(
                "SET LOCAL app.current_org_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'"
        );
    }

    @Test
    void afterBeginSkipsWhenNoAuthenticatedPrincipal() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        // SecurityContext is empty

        TenantRlsTransactionListener listener = new TenantRlsTransactionListener(jdbc);
        listener.afterBegin(mock(TransactionExecution.class), null);

        verifyNoInteractions(jdbc);
    }

    @Test
    void afterBeginSkipsWhenTransactionFailedToBegin() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        authenticateAs(ORG_ID);

        TenantRlsTransactionListener listener = new TenantRlsTransactionListener(jdbc);
        listener.afterBegin(mock(TransactionExecution.class), new RuntimeException("begin failed"));

        verifyNoInteractions(jdbc);
    }

    @Test
    void afterBeginSqlMatchesTenantConnectionInterceptorFormat() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        UUID orgId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        authenticateAs(orgId);

        TenantRlsTransactionListener listener = new TenantRlsTransactionListener(jdbc);
        listener.afterBegin(mock(TransactionExecution.class), null);

        String expectedSql = TenantConnectionInterceptor.setTenantSql(orgId);
        verify(jdbc).execute(expectedSql);
    }
}
