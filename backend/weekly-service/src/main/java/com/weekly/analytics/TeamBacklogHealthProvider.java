package com.weekly.analytics;

import com.weekly.analytics.dto.TeamBacklogHealth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads team backlog health metrics from the {@code mv_team_backlog_health}
 * materialized view (introduced in V18).
 *
 * <p>The view is refreshed every 15 minutes by {@link MaterializedViewRefreshJob}.
 * Queries filter by {@code org_id} explicitly because materialized views do not
 * support Row-Level Security.
 *
 * <p>Used by the TeamDashboardPage "Backlog Health" section.
 */
@Component
public class TeamBacklogHealthProvider {

    private final NamedParameterJdbcTemplate namedJdbc;

    public TeamBacklogHealthProvider(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    /**
     * Returns the backlog health snapshot for a specific team.
     *
     * @param orgId  the organisation ID
     * @param teamId the team ID
     * @return an {@link Optional} containing the health snapshot, or empty if the team
     *         has no open issues (and therefore no row in the materialized view)
     */
    @Transactional(readOnly = true)
    public Optional<TeamBacklogHealth> getTeamHealth(UUID orgId, UUID teamId) {
        String sql = """
                SELECT
                    team_id,
                    open_issue_count,
                    avg_issue_age_days,
                    blocked_count,
                    build_count,
                    maintain_count,
                    collaborate_count,
                    learn_count,
                    avg_cycle_time_days
                FROM mv_team_backlog_health
                WHERE org_id = :orgId
                  AND team_id = :teamId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("teamId", teamId);

        List<TeamBacklogHealth> results = namedJdbc.query(sql, params, (rs, rowNum) ->
                new TeamBacklogHealth(
                        rs.getObject("team_id", UUID.class).toString(),
                        rs.getLong("open_issue_count"),
                        rs.getDouble("avg_issue_age_days"),
                        rs.getLong("blocked_count"),
                        rs.getLong("build_count"),
                        rs.getLong("maintain_count"),
                        rs.getLong("collaborate_count"),
                        rs.getLong("learn_count"),
                        rs.getDouble("avg_cycle_time_days")
                )
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns backlog health snapshots for all teams in the organisation.
     *
     * <p>Teams with no open issues do not appear in the materialized view and
     * are therefore not included in the returned list.
     *
     * @param orgId the organisation ID
     * @return list of health snapshots, one per team with open issues
     */
    @Transactional(readOnly = true)
    public List<TeamBacklogHealth> getOrgHealth(UUID orgId) {
        String sql = """
                SELECT
                    team_id,
                    open_issue_count,
                    avg_issue_age_days,
                    blocked_count,
                    build_count,
                    maintain_count,
                    collaborate_count,
                    learn_count,
                    avg_cycle_time_days
                FROM mv_team_backlog_health
                WHERE org_id = :orgId
                ORDER BY open_issue_count DESC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId);

        return namedJdbc.query(sql, params, (rs, rowNum) ->
                new TeamBacklogHealth(
                        rs.getObject("team_id", UUID.class).toString(),
                        rs.getLong("open_issue_count"),
                        rs.getDouble("avg_issue_age_days"),
                        rs.getLong("blocked_count"),
                        rs.getLong("build_count"),
                        rs.getLong("maintain_count"),
                        rs.getLong("collaborate_count"),
                        rs.getLong("learn_count"),
                        rs.getDouble("avg_cycle_time_days")
                )
        );
    }
}
