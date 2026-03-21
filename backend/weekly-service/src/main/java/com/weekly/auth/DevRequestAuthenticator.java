package com.weekly.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev/test implementation of {@link RequestAuthenticator}.
 *
 * <p>Active in {@code local}, {@code dev}, and {@code test} profiles.
 * Extracts the {@link UserPrincipal} primarily from a structured
 * <strong>dev token</strong> carried in the {@code Authorization: Bearer}
 * header, with fallback to legacy {@code X-*} headers for backwards
 * compatibility during migration.
 *
 * <h3>Structured dev token format</h3>
 * <pre>dev:&lt;userId&gt;:&lt;orgId&gt;:&lt;comma-separated-roles&gt;</pre>
 * Example: {@code dev:c000…0001:a000…0001:IC,MANAGER}
 *
 * <h3>Legacy header fallback</h3>
 * If no structured dev token is present the authenticator falls back to:
 * <ul>
 *   <li>{@code X-Org-Id} — required; 401 if absent or not a valid UUID</li>
 *   <li>{@code X-User-Id} — optional; defaults to anonymous sentinel</li>
 *   <li>{@code X-Roles}   — optional; comma-separated list of role names</li>
 * </ul>
 *
 * <p><strong>Never enable this in production.</strong>
 */
@Component
@Profile({"local", "dev", "test"})
public class DevRequestAuthenticator implements RequestAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(DevRequestAuthenticator.class);

    /** Prefix that identifies a structured dev token. */
    static final String DEV_TOKEN_PREFIX = "dev:";

    /**
     * Sentinel UUID used when {@code X-User-Id} is not supplied.
     * Callers that need a real user ID must always send the header.
     */
    public static final UUID ANONYMOUS_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public UserPrincipal authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        // 1. Try structured dev token from Bearer header
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length()).trim();
            if (token.startsWith(DEV_TOKEN_PREFIX)) {
                return parseDevToken(token);
            }
            // A non-dev token (e.g. a real JWT) — fall back to X- headers
            // but log a warning so developers know the contract is changing.
            LOG.warn("Bearer token present but not a dev token — falling back to X- headers. "
                    + "In production JwksRequestAuthenticator handles real JWTs.");
        }

        // 2. Legacy X- header fallback
        return authenticateFromHeaders(request);
    }

    /**
     * Parse a structured dev token.
     *
     * @param token the full token string starting with {@code dev:}
     * @return the extracted {@link UserPrincipal}
     * @throws AuthenticationException if the token is malformed
     */
    UserPrincipal parseDevToken(String token) {
        // Format: dev:<userId>:<orgId>:<roles>
        String payload = token.substring(DEV_TOKEN_PREFIX.length());
        String[] parts = payload.split(":", 3);
        if (parts.length < 2) {
            throw new AuthenticationException(
                    "Malformed dev token: expected dev:<userId>:<orgId>[:<roles>]"
            );
        }

        UUID userId;
        try {
            userId = UUID.fromString(parts[0].trim());
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException(
                    "Invalid userId in dev token: not a valid UUID"
            );
        }

        UUID orgId;
        try {
            orgId = UUID.fromString(parts[1].trim());
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException(
                    "Invalid orgId in dev token: not a valid UUID"
            );
        }

        Set<String> roles = new HashSet<>();
        if (parts.length == 3 && !parts[2].isBlank()) {
            for (String role : parts[2].split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    roles.add(trimmed);
                }
            }
        }

        return new UserPrincipal(userId, orgId, Set.copyOf(roles));
    }

    /**
     * Legacy authentication via {@code X-*} request headers.
     */
    private UserPrincipal authenticateFromHeaders(HttpServletRequest request) {
        String orgIdHeader = request.getHeader("X-Org-Id");
        if (orgIdHeader == null || orgIdHeader.isBlank()) {
            throw new AuthenticationException(
                    "Missing required header: X-Org-Id"
            );
        }

        UUID orgId;
        try {
            orgId = UUID.fromString(orgIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException(
                    "Invalid X-Org-Id header: not a valid UUID"
            );
        }

        String userIdHeader = request.getHeader("X-User-Id");
        UUID userId;
        if (userIdHeader == null || userIdHeader.isBlank()) {
            userId = ANONYMOUS_USER_ID;
        } else {
            try {
                userId = UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new AuthenticationException(
                        "Invalid X-User-Id header: not a valid UUID"
                );
            }
        }

        String rolesHeader = request.getHeader("X-Roles");
        Set<String> roles = new HashSet<>();
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    roles.add(trimmed);
                }
            }
        }

        return new UserPrincipal(userId, orgId, Set.copyOf(roles));
    }
}
