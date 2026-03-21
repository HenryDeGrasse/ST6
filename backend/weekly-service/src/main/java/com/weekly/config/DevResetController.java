package com.weekly.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

        // Clear in dependency order
        jdbc.execute("DELETE FROM weekly_commit_actuals");
        jdbc.execute("DELETE FROM manager_reviews");
        jdbc.execute("DELETE FROM notifications");
        jdbc.execute("DELETE FROM weekly_commits");
        jdbc.execute("DELETE FROM weekly_plans");
        jdbc.execute("DELETE FROM idempotency_keys");

        // Re-apply seed SQL
        try {
            var resource = new ClassPathResource("seed-data.sql");
            String seedSql = resource.getContentAsString(StandardCharsets.UTF_8);
            jdbc.execute(seedSql);
            LOG.info("DEV RESET: seed data applied successfully");
        } catch (IOException e) {
            LOG.error("DEV RESET: failed to read seed-data.sql", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed to read seed file: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("status", "ok", "message", "Database reset and re-seeded"));
    }
}
