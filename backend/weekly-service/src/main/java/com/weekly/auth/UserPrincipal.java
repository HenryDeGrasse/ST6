package com.weekly.auth;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the authenticated user extracted from a validated JWT.
 * All downstream services receive this rather than the raw token.
 *
 * <p>{@code orgId} comes exclusively from the JWT {@code orgId} claim —
 * never from a request parameter or path segment (§9.1).
 *
 * <p>{@code timeZone} carries the user's preferred IANA timezone so scheduled
 * workflows can operate in the user's local planning window. When the upstream
 * identity system does not provide a timezone, the value safely falls back to
 * {@code UTC}.
 */
public record UserPrincipal(
        UUID userId,
        UUID orgId,
        Set<String> roles,
        String timeZone
) {
    public static final String DEFAULT_TIME_ZONE = "UTC";

    public UserPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(orgId, "orgId must not be null");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        timeZone = normalizeTimeZone(timeZone);
    }

    public UserPrincipal(UUID userId, UUID orgId, Set<String> roles) {
        this(userId, orgId, roles, DEFAULT_TIME_ZONE);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isManager() {
        return hasRole("MANAGER");
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    private static String normalizeTimeZone(String rawTimeZone) {
        if (rawTimeZone == null || rawTimeZone.isBlank()) {
            return DEFAULT_TIME_ZONE;
        }
        try {
            return ZoneId.of(rawTimeZone.trim()).getId();
        } catch (RuntimeException ignored) {
            return DEFAULT_TIME_ZONE;
        }
    }
}
