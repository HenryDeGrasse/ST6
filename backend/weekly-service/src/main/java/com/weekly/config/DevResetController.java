package com.weekly.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoint to reset and re-seed the database.
 * <strong>Never enable in production.</strong>
 *
 * <p>POST /api/v1/dev/reset-seed clears all plan/commit data
 * and re-applies the seed-data.sql script.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("local")
public class DevResetController {

    private static final Logger LOG = LoggerFactory.getLogger(DevResetController.class);

    private final JdbcTemplate jdbc;

    public DevResetController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/reset-seed")
    public ResponseEntity<Map<String, String>> resetAndSeed() {
        LOG.warn("DEV RESET: clearing all plan data and re-seeding");

        // Clear ALL tables in strict dependency order.
        // Use TRUNCATE CASCADE for clean slate — handles all FK chains.
        jdbc.execute("TRUNCATE TABLE weekly_assignment_actuals, weekly_assignments, "
                + "issue_activities, "
                + "progress_entries, external_ticket_links, "
                + "weekly_commit_actuals, manager_reviews, "
                + "weekly_commits, weekly_plans, "
                + "issues, team_members, teams, "
                + "notifications, idempotency_keys, "
                + "user_model_snapshots, "
                + "planning_copilot_snapshots "
                + "CASCADE");

        // Re-apply seed SQL using ScriptUtils for proper statement parsing
        try {
            var resource = new ClassPathResource("seed-data.sql");
            var dataSource = jdbc.getDataSource();
            if (dataSource == null) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("status", "error", "message", "No DataSource available"));
            }
            try (var conn = dataSource.getConnection()) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn, resource);
            }
            LOG.info("DEV RESET: seed data applied successfully");
        } catch (Exception e) {
            LOG.error("DEV RESET: failed to apply seed-data.sql", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Seed failed: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("status", "ok", "message", "Database reset and re-seeded"));
    }
}
