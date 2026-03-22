package com.weekly.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link MaterializedViewRefreshJob}.
 *
 * <p>Mocks {@link JdbcTemplate} to avoid a live database and uses
 * {@link SimpleMeterRegistry} to assert Micrometer counter values directly.
 * Covers all five materialized views: two V9 commit-based views and
 * three V18 assignment-based views.
 */
class MaterializedViewRefreshJobTest {

    /** Total number of materialized views refreshed per cycle. */
    private static final int TOTAL_VIEWS = 5;

    private JdbcTemplate jdbcTemplate;
    private SimpleMeterRegistry meterRegistry;
    private MaterializedViewRefreshJob job;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new MaterializedViewRefreshJob(jdbcTemplate, meterRegistry);
    }

    // ── SQL execution ─────────────────────────────────────────────────────────

    @Nested
    class SqlExecution {

        /**
         * Verifies that {@code refreshMaterializedViews()} issues exactly five
         * {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} statements — one for each
         * view defined in the job (two V9 + three V18).
         */
        @Test
        void executesRefreshConcurrentlyForAllFiveViews() {
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            job.refreshMaterializedViews();

            verify(jdbcTemplate, times(TOTAL_VIEWS)).execute(sqlCaptor.capture());
            List<String> sqls = sqlCaptor.getAllValues();

            // V9 commit-based views
            assertTrue(sqls.contains(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY "
                            + MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY),
                    "Expected SQL for mv_outcome_coverage_weekly not found");
            assertTrue(sqls.contains(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY "
                            + MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY),
                    "Expected SQL for mv_user_weekly_summary not found");

            // V18 assignment-based views
            assertTrue(sqls.contains(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY "
                            + MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY_V2),
                    "Expected SQL for mv_outcome_coverage_weekly_v2 not found");
            assertTrue(sqls.contains(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY "
                            + MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY_V2),
                    "Expected SQL for mv_user_weekly_summary_v2 not found");
            assertTrue(sqls.contains(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY "
                            + MaterializedViewRefreshJob.MV_TEAM_BACKLOG_HEALTH),
                    "Expected SQL for mv_team_backlog_health not found");
        }

        /**
         * Verifies that every SQL string passed to {@link JdbcTemplate#execute}
         * contains the {@code CONCURRENTLY} keyword, ensuring non-blocking refreshes.
         */
        @Test
        void sqlStringsContainConcurrentlyKeyword() {
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            job.refreshMaterializedViews();

            verify(jdbcTemplate, times(TOTAL_VIEWS)).execute(sqlCaptor.capture());
            for (String sql : sqlCaptor.getAllValues()) {
                assertTrue(sql.contains("CONCURRENTLY"),
                        "SQL must contain CONCURRENTLY keyword: " + sql);
            }
        }
    }

    // ── success counter ───────────────────────────────────────────────────────

    @Nested
    class SuccessCounter {

        /**
         * After a successful refresh, the {@code analytics_mv_refresh_total} counter
         * tagged with {@code result=success} must be incremented once per view for
         * all five views.
         */
        @Test
        void incrementsSuccessCounterForEachViewOnSuccessfulRefresh() {
            job.refreshMaterializedViews();

            // V9 views
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY),
                    "Success counter for mv_outcome_coverage_weekly must be 1");
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY),
                    "Success counter for mv_user_weekly_summary must be 1");

            // V18 views
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY_V2),
                    "Success counter for mv_outcome_coverage_weekly_v2 must be 1");
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY_V2),
                    "Success counter for mv_user_weekly_summary_v2 must be 1");
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_TEAM_BACKLOG_HEALTH),
                    "Success counter for mv_team_backlog_health must be 1");
        }

        /**
         * Before any refresh has run, no success counter should be registered.
         */
        @Test
        void successCounterIsAbsentBeforeAnyRefresh() {
            Counter counter = meterRegistry
                    .find("analytics_mv_refresh_total")
                    .tags("view", MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY,
                            "result", "success")
                    .counter();
            assertNull(counter, "No success counter should be registered before any refresh");
        }
    }

    // ── failure counter ───────────────────────────────────────────────────────

    @Nested
    class FailureCounter {

        /**
         * When {@link JdbcTemplate#execute} throws for every call, the
         * {@code analytics_mv_refresh_total} counter tagged with
         * {@code result=failure} must be incremented once per view for all views,
         * and the success counter must remain at zero.
         */
        @Test
        void incrementsFailureCounterWhenJdbcTemplateThrows() {
            doThrow(new RuntimeException("DB connection error"))
                    .when(jdbcTemplate).execute(anyString());
            ListAppender<ILoggingEvent> logAppender = attachLogAppender();

            try {
                job.refreshMaterializedViews();
            } finally {
                detachLogAppender(logAppender);
            }

            // V9 views
            assertEquals(1.0,
                    failureCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY),
                    "Failure counter for mv_outcome_coverage_weekly must be 1");
            assertEquals(1.0,
                    failureCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY),
                    "Failure counter for mv_user_weekly_summary must be 1");
            assertEquals(0.0,
                    successCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY),
                    "Success counter for mv_outcome_coverage_weekly must stay 0 on failure");
            assertEquals(0.0,
                    successCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY),
                    "Success counter for mv_user_weekly_summary must stay 0 on failure");

            // V18 views
            assertEquals(1.0,
                    failureCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY_V2),
                    "Failure counter for mv_outcome_coverage_weekly_v2 must be 1");
            assertEquals(1.0,
                    failureCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY_V2),
                    "Failure counter for mv_user_weekly_summary_v2 must be 1");
            assertEquals(1.0,
                    failureCount(MaterializedViewRefreshJob.MV_TEAM_BACKLOG_HEALTH),
                    "Failure counter for mv_team_backlog_health must be 1");

            assertTrue(logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                            && event.getFormattedMessage().contains("failed to refresh "
                                    + MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY)),
                    "Expected an error log for mv_outcome_coverage_weekly refresh failure");
            assertTrue(logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                            && event.getFormattedMessage().contains("failed to refresh "
                                    + MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY)),
                    "Expected an error log for mv_user_weekly_summary refresh failure");
        }

        /**
         * When only the first V9 view fails, the per-view counters must reflect the
         * individual outcomes: failure for the first view, success for all others.
         */
        @Test
        void incrementsFailureOnlyForFailingViewAndSuccessForPassingView() {
            doThrow(new RuntimeException("first view failure"))
                    .when(jdbcTemplate)
                    .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY "
                            + MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY);

            job.refreshMaterializedViews();

            assertEquals(1.0,
                    failureCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY),
                    "Failure counter for the failing view must be 1");
            assertEquals(0.0,
                    successCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY),
                    "Success counter for the failing view must remain 0");
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY),
                    "Success counter for mv_user_weekly_summary must be 1");
            assertEquals(0.0,
                    failureCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY),
                    "Failure counter for mv_user_weekly_summary must remain 0");
            // V18 views should all succeed
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_OUTCOME_COVERAGE_WEEKLY_V2),
                    "Success counter for mv_outcome_coverage_weekly_v2 must be 1");
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_USER_WEEKLY_SUMMARY_V2),
                    "Success counter for mv_user_weekly_summary_v2 must be 1");
            assertEquals(1.0,
                    successCount(MaterializedViewRefreshJob.MV_TEAM_BACKLOG_HEALTH),
                    "Success counter for mv_team_backlog_health must be 1");
        }
    }

    // ── helper methods ─────────────────────────────────────────────────────────

    private double successCount(String viewName) {
        Counter counter = meterRegistry
                .find("analytics_mv_refresh_total")
                .tags("view", viewName, "result", "success")
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double failureCount(String viewName) {
        Counter counter = meterRegistry
                .find("analytics_mv_refresh_total")
                .tags("view", viewName, "result", "failure")
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(MaterializedViewRefreshJob.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        return logAppender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> logAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(MaterializedViewRefreshJob.class);
        logger.detachAppender(logAppender);
        logAppender.stop();
    }
}
