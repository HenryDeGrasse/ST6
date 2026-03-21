package com.weekly.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that refreshes the analytics materialized views introduced in
 * V9 ({@code mv_outcome_coverage_weekly} and {@code mv_user_weekly_summary}).
 *
 * <p>Uses {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} so that read queries
 * against the views are never blocked during refresh (requires the unique
 * indexes created by the V9 migration).
 *
 * <p>Enabled via {@code analytics.refresh.enabled=true} (worker profile only).
 * Runs every 15 minutes with a 60-second startup delay.
 */
@Component
@ConditionalOnProperty(name = "analytics.refresh.enabled", havingValue = "true")
public class MaterializedViewRefreshJob {

    private static final Logger LOG = LoggerFactory.getLogger(MaterializedViewRefreshJob.class);

    static final String MV_OUTCOME_COVERAGE_WEEKLY = "mv_outcome_coverage_weekly";
    static final String MV_USER_WEEKLY_SUMMARY = "mv_user_weekly_summary";

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public MaterializedViewRefreshJob(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Refreshes both analytics materialized views concurrently.
     * Runs every 15 minutes (fixedRate = 900_000 ms) with a 60-second
     * initial delay to allow the application to finish startup.
     */
    @Scheduled(fixedRate = 900_000, initialDelay = 60_000)
    public void refreshMaterializedViews() {
        LOG.info("MaterializedViewRefreshJob: starting refresh of analytics materialized views");

        int successfulViews = 0;
        successfulViews += refreshView(MV_OUTCOME_COVERAGE_WEEKLY) ? 1 : 0;
        successfulViews += refreshView(MV_USER_WEEKLY_SUMMARY) ? 1 : 0;

        LOG.info(
                "MaterializedViewRefreshJob: completed refresh of analytics materialized views "
                        + "(successfulViews={}, failedViews={})",
                successfulViews,
                2 - successfulViews
        );
    }

    /**
     * Executes a concurrent refresh for a single materialized view.
     * Records a Micrometer counter on success or failure.
     *
     * @param viewName the unqualified name of the materialized view
     * @return {@code true} when the refresh succeeds, otherwise {@code false}
     */
    boolean refreshView(String viewName) {
        LOG.info("MaterializedViewRefreshJob: starting REFRESH MATERIALIZED VIEW CONCURRENTLY {}", viewName);
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName);
            meterRegistry
                    .counter("analytics_mv_refresh_total", "view", viewName, "result", "success")
                    .increment();
            LOG.info("MaterializedViewRefreshJob: completed refresh of {}", viewName);
            return true;
        } catch (Exception e) {
            meterRegistry
                    .counter("analytics_mv_refresh_total", "view", viewName, "result", "failure")
                    .increment();
            LOG.error("MaterializedViewRefreshJob: failed to refresh {}: {}", viewName, e.getMessage(), e);
            return false;
        }
    }
}
