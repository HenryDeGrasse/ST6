package com.weekly.auth;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link OrgGraphClient} for development and testing.
 *
 * <p>In production, this would be replaced with a real adapter that calls
 * the PA directory service or HRIS API with Redis caching (TTL: 15 min).
 */
@Component
public class InMemoryOrgGraphClient implements OrgGraphClient {

    private final Map<String, List<UUID>> directReportsMap = new ConcurrentHashMap<>();
    private final Map<UUID, UserDirectoryEntry> userDirectory = new ConcurrentHashMap<>();

    @Override
    public List<UUID> getDirectReports(UUID orgId, UUID managerId) {
        String key = orgId + ":" + managerId;
        return directReportsMap.getOrDefault(key, List.of());
    }

    @Override
    public List<DirectReport> getDirectReportsWithNames(UUID orgId, UUID managerId) {
        return getDirectReports(orgId, managerId).stream()
                .map(id -> new DirectReport(id, displayNameFor(id)))
                .toList();
    }

    @Override
    public List<OrgRosterEntry> getOrgRoster(UUID orgId) {
        Map<UUID, UUID> managerByUserId = new LinkedHashMap<>();
        directReportsMap.forEach((key, reports) -> {
            String[] parts = key.split(":", 2);
            if (parts.length != 2 || !orgId.toString().equals(parts[0])) {
                return;
            }
            UUID managerId = UUID.fromString(parts[1]);
            managerByUserId.putIfAbsent(managerId, null);
            for (UUID reportId : reports) {
                managerByUserId.put(reportId, managerId);
            }
        });

        userDirectory.keySet().forEach(userId -> managerByUserId.putIfAbsent(userId, null));

        return managerByUserId.entrySet().stream()
                .map(entry -> {
                    UUID userId = entry.getKey();
                    UserDirectoryEntry directoryEntry = userDirectory.get(userId);
                    return new OrgRosterEntry(
                            userId,
                            directoryEntry != null ? directoryEntry.displayName() : userId.toString(),
                            entry.getValue(),
                            directoryEntry != null ? directoryEntry.timeZone() : UserPrincipal.DEFAULT_TIME_ZONE);
                })
                .sorted(java.util.Comparator
                        .comparing(OrgRosterEntry::displayName)
                        .thenComparing(entry -> entry.userId().toString()))
                .toList();
    }

    /**
     * Registers direct reports for a manager. For testing and development.
     */
    public void setDirectReports(UUID orgId, UUID managerId, List<UUID> reportIds) {
        directReportsMap.put(orgId + ":" + managerId, List.copyOf(reportIds));
    }

    /**
     * Registers a display name for a user. For testing and development.
     *
     * @param userId      the user ID
     * @param displayName the human-readable display name
     */
    public void registerUser(UUID userId, String displayName) {
        registerUser(userId, displayName, UserPrincipal.DEFAULT_TIME_ZONE);
    }

    /**
     * Registers a display name and timezone for a user. For testing and development.
     */
    public void registerUser(UUID userId, String displayName, String timeZone) {
        userDirectory.put(userId, new UserDirectoryEntry(displayName, normalizeTimeZone(timeZone)));
    }

    /**
     * Clears all cached direct report and user data. For testing.
     */
    public void clear() {
        directReportsMap.clear();
        userDirectory.clear();
    }

    private String displayNameFor(UUID userId) {
        UserDirectoryEntry entry = userDirectory.get(userId);
        return entry != null ? entry.displayName() : userId.toString();
    }

    private String normalizeTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return UserPrincipal.DEFAULT_TIME_ZONE;
        }
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (RuntimeException ignored) {
            return UserPrincipal.DEFAULT_TIME_ZONE;
        }
    }

    private record UserDirectoryEntry(String displayName, String timeZone) {
    }
}
