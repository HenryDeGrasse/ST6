package com.weekly.auth;

import java.util.UUID;

/**
 * A direct report with an optional display name.
 *
 * <p>Used by the team dashboard to show human-readable names instead
 * of raw UUIDs. The display name comes from the org graph / HRIS API.
 *
 * @param userId      the direct report's user ID
 * @param displayName the human-readable name, or {@code userId.toString()} as fallback
 */
public record DirectReport(UUID userId, String displayName) {}
