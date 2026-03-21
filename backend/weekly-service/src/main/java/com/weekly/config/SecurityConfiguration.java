package com.weekly.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.JwtAuthenticationFilter;
import com.weekly.auth.RequestAuthenticator;
import com.weekly.idempotency.IdempotencyKeyFilter;
import com.weekly.idempotency.IdempotencyKeyService;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the weekly-service.
 *
 * <p>Filter chain rules:
 * <ul>
 *   <li>{@code GET /api/v1/health} — permitted without authentication</li>
 *   <li>{@code POST /api/v1/integrations/webhook/**} — permitted without authentication</li>
 *   <li>{@code /actuator/**}       — permitted without authentication</li>
 *   <li>All other {@code /api/v1/**} endpoints — require a valid principal</li>
 * </ul>
 *
 * <p>Sessions are stateless (JWT / header-based auth). CSRF is disabled
 * because every request carries its own credential.
 *
 * <p>The active {@link RequestAuthenticator} implementation is injected;
 * in local/dev/test profiles this is the header-based
 * {@code DevRequestAuthenticator}; in prod/staging it will be the JWKS
 * {@code JwksRequestAuthenticator}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"code\":\"UNAUTHORIZED\","
            + "\"message\":\"Authentication required: missing or invalid credentials\"}}";

    private final RequestAuthenticator requestAuthenticator;
    private final IdempotencyKeyService idempotencyKeyService;
    private final ObjectMapper objectMapper;

    public SecurityConfiguration(
            RequestAuthenticator requestAuthenticator,
            IdempotencyKeyService idempotencyKeyService,
            ObjectMapper objectMapper
    ) {
        this.requestAuthenticator = requestAuthenticator;
        this.idempotencyKeyService = idempotencyKeyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates the {@link JwtAuthenticationFilter} used exclusively inside the
     * Spring Security filter chain.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(requestAuthenticator);
    }

    /**
     * Prevents Spring Boot from auto-registering the authentication filter as a
     * top-level servlet filter in addition to the Spring Security chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Creates the {@link IdempotencyKeyFilter} used exclusively inside the
     * Spring Security filter chain (after JWT authentication).
     */
    @Bean
    public IdempotencyKeyFilter idempotencyKeyFilter() {
        return new IdempotencyKeyFilter(idempotencyKeyService, objectMapper);
    }

    /**
     * Prevents Spring Boot from auto-registering the idempotency filter as a
     * top-level servlet filter in addition to the Spring Security chain.
     */
    @Bean
    public FilterRegistrationBean<IdempotencyKeyFilter> idempotencyKeyFilterRegistration(
            IdempotencyKeyFilter idempotencyKeyFilter
    ) {
        FilterRegistrationBean<IdempotencyKeyFilter> registration =
                new FilterRegistrationBean<>(idempotencyKeyFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/integrations/webhook/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authException) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write(UNAUTHORIZED_BODY);
                })
                .accessDeniedHandler((req, res, accessDeniedException) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write(
                        "{\"error\":{\"code\":\"FORBIDDEN\","
                        + "\"message\":\"Access denied\"}}"
                    );
                })
            )
            .addFilterBefore(
                jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class
            )
            .addFilterAfter(
                idempotencyKeyFilter(),
                JwtAuthenticationFilter.class
            );

        return http.build();
    }
}
