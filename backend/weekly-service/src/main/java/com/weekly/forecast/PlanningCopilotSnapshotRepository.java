package com.weekly.forecast;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistence layer for daily planning-copilot snapshots.
 *
 * <p>Each snapshot is keyed by (org, manager, weekStart, snapshotDate).
 * Only one snapshot per key is stored; regeneration overwrites.
 */
@Repository
public class PlanningCopilotSnapshotRepository {

    private final JdbcTemplate jdbc;

    public PlanningCopilotSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Find the snapshot for today (or a specific date).
     */
    public Optional<String> findSnapshot(UUID orgId, UUID managerId, LocalDate weekStart, LocalDate snapshotDate) {
        var rows = jdbc.queryForList(
                """
                SELECT payload_json::text
                FROM planning_copilot_snapshots
                WHERE org_id = ? AND manager_user_id = ? AND week_start = ? AND snapshot_date = ?
                LIMIT 1
                """,
                orgId, managerId, weekStart, snapshotDate);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) rows.get(0).get("payload_json"));
    }

    /**
     * Upsert today's snapshot (insert or replace).
     */
    public void upsertSnapshot(UUID orgId, UUID managerId, LocalDate weekStart, LocalDate snapshotDate, String payloadJson) {
        jdbc.update(
                """
                INSERT INTO planning_copilot_snapshots (org_id, manager_user_id, week_start, snapshot_date, payload_json)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (org_id, manager_user_id, week_start, snapshot_date)
                DO UPDATE SET payload_json = EXCLUDED.payload_json, updated_at = NOW()
                """,
                orgId, managerId, weekStart, snapshotDate, payloadJson);
    }
}
