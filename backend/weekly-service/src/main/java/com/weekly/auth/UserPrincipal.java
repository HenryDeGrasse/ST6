package com.weekly.auth;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the authenticated user extracted from a validated JWT.
 * All downstream services receive this rather than the raw token.
 *
 * <p>{@code orgId} comes exclusively from the JWT {@code orgId} claim —
 * never from a request parameter or path segment (§9.1).
 */
public record UserPrincipal(
        UUID userId,
        UUID orgId,
        Set<String> roles
) {
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isManager() {
        return hasRole("MANAGER");
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
