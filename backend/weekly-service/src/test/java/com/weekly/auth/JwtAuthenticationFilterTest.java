package com.weekly.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 */
class JwtAuthenticationFilterTest {

    private static final UserPrincipal PRINCIPAL = new UserPrincipal(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            Set.of("MANAGER")
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void populatesSecurityContextAndMdcOnSuccessfulAuthentication() throws Exception {
        RequestAuthenticator authenticator = request -> PRINCIPAL;
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authenticator);
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            Object authenticatedPrincipal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();
            assertEquals(PRINCIPAL, authenticatedPrincipal);
            assertEquals(PRINCIPAL.orgId().toString(), MDC.get("orgId"));
            assertEquals(PRINCIPAL.userId().toString(), MDC.get("userId"));
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        };

        filter.doFilter(request, response, chain);
    }

    @Test
    void clearsSecurityContextAndMdcOnAuthenticationFailure() throws Exception {
        RequestAuthenticator authenticator = request -> {
            throw new AuthenticationException("invalid credentials");
        };
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authenticator);
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(MDC.get("orgId"));
            assertNull(MDC.get("userId"));
        };

        filter.doFilter(request, response, chain);
    }
}
