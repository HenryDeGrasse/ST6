package com.weekly.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that authenticates every incoming request by delegating
 * to the active {@link RequestAuthenticator}.
 *
 * <p>On success the authenticated {@link UserPrincipal} is stored in the
 * Spring Security {@link SecurityContextHolder} so that request-scoped
 * components such as {@link AuthenticatedUserContext} can access it.
 *
 * <p>On failure the security context is left empty; Spring Security's
 * {@code AuthorizationFilter} then rejects the request with a 401 response
 * for any endpoint that requires authentication.
 *
 * <p>This filter is registered programmatically via
 * {@link com.weekly.config.SecurityConfiguration}; it must NOT be annotated
 * with {@code @Component} to avoid double-registration by Spring Boot.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RequestAuthenticator requestAuthenticator;

    public JwtAuthenticationFilter(RequestAuthenticator requestAuthenticator) {
        this.requestAuthenticator = requestAuthenticator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            UserPrincipal principal = requestAuthenticator.authenticate(request);

            List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put("orgId", principal.orgId().toString());
            MDC.put("userId", principal.userId().toString());

        } catch (AuthenticationException ex) {
            // Leave SecurityContext empty; Spring Security will enforce
            // 401 for protected endpoints via the configured AuthenticationEntryPoint.
            SecurityContextHolder.clearContext();
            MDC.remove("orgId");
            MDC.remove("userId");
        }

        filterChain.doFilter(request, response);
    }
}
