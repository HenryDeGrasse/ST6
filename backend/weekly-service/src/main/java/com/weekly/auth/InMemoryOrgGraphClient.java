package com.weekly.auth;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link OrgGraphClient} for development and testing.
 *
 * <p>In production, this would be replaced with a real adapter that calls
 * the PA directory service or HRIS API with Redis caching (TTL: 15 min).
 */
@Component
public class InMemoryOrgGraphClient implements OrgGraphClient {

    private final Map<String, List<UUID>> directReportsMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> userDisplayNames = new ConcurrentHashMap<>();

    @Override
    public List<UUID> getDirectReports(UUID orgId, UUID managerId) {
        String key = orgId + ":" + managerId;
        return directReportsMap.getOrDefault(key, List.of());
    }

    @Override
    public List<DirectReport> getDirectReportsWithNames(UUID orgId, UUID managerId) {
        return getDirectReports(orgId, managerId).stream()
                .map(id -> new DirectReport(id, userDisplayNames.getOrDefault(id, id.toString())))
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
        userDisplayNames.put(userId, displayName);
    }

    /**
     * Clears all cached direct report and user data. For testing.
     */
    public void clear() {
        directReportsMap.clear();
        userDisplayNames.clear();
    }
}
