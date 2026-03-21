package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.shared.ManagerInsightDataProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying that {@link PromptBuilder#buildManagerInsightsMessages}
 * correctly incorporates the multi-week historical context section.
 */
class PromptBuilderHistoricalContextTest {

    private static final String WEEK_START = "2026-03-09";

    private ManagerInsightDataProvider.ManagerWeekContext buildContext(
            List<ManagerInsightDataProvider.CarryForwardStreak> streaks,
            List<ManagerInsightDataProvider.OutcomeCoverageTrend> trends,
            List<ManagerInsightDataProvider.LateLockPattern> lateLocks,
            ManagerInsightDataProvider.ReviewTurnaroundStats turnaround
    ) {
        return new ManagerInsightDataProvider.ManagerWeekContext(
                WEEK_START,
                new ManagerInsightDataProvider.ReviewCounts(0, 0, 0),
                List.of(),
                List.of(),
                streaks,
                trends,
                lateLocks,
                turnaround
        );
    }

    @Test
    void omitsHistoricalSectionWhenAllListsEmpty() {
        ManagerInsightDataProvider.ManagerWeekContext ctx =
                buildContext(List.of(), List.of(), List.of(), null);

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertFalse(contextMessage.contains("Multi-week historical context"),
                "Historical section should be absent when there are no historical signals");
    }

    @Test
    void includesCarryForwardStreakSection() {
        List<ManagerInsightDataProvider.CarryForwardStreak> streaks = List.of(
                new ManagerInsightDataProvider.CarryForwardStreak(
                        "user-abc", 3, List.of("Fix login bug", "Write docs"))
        );

        ManagerInsightDataProvider.ManagerWeekContext ctx =
                buildContext(streaks, List.of(), List.of(), null);

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertTrue(contextMessage.contains("Carry-forward streaks"),
                "Should include carry-forward streaks section");
        assertTrue(contextMessage.contains("user-abc"), "Should include user ID");
        assertTrue(contextMessage.contains("streakWeeks: 3"), "Should include streak length");
        assertTrue(contextMessage.contains("Fix login bug"), "Should include carried item title");
    }

    @Test
    void includesOutcomeCoverageTrendSection() {
        List<ManagerInsightDataProvider.WeeklyCommitCount> weekCounts = List.of(
                new ManagerInsightDataProvider.WeeklyCommitCount("2026-02-16", 4),
                new ManagerInsightDataProvider.WeeklyCommitCount("2026-02-23", 3),
                new ManagerInsightDataProvider.WeeklyCommitCount("2026-03-02", 2),
                new ManagerInsightDataProvider.WeeklyCommitCount("2026-03-09", 1)
        );
        List<ManagerInsightDataProvider.OutcomeCoverageTrend> trends = List.of(
                new ManagerInsightDataProvider.OutcomeCoverageTrend(
                        "outcome-123", "Grow ARR", weekCounts)
        );

        ManagerInsightDataProvider.ManagerWeekContext ctx =
                buildContext(List.of(), trends, List.of(), null);

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertTrue(contextMessage.contains("Outcome coverage trends"),
                "Should include outcome coverage trends section");
        assertTrue(contextMessage.contains("Grow ARR"), "Should include outcome name");
        assertTrue(contextMessage.contains("2026-02-16:4"), "Should include oldest week count");
        assertTrue(contextMessage.contains("2026-03-09:1"), "Should include most recent week count");
    }

    @Test
    void includesLateLockPatternSection() {
        List<ManagerInsightDataProvider.LateLockPattern> lateLocks = List.of(
                new ManagerInsightDataProvider.LateLockPattern("user-xyz", 2, 4)
        );

        ManagerInsightDataProvider.ManagerWeekContext ctx =
                buildContext(List.of(), List.of(), lateLocks, null);

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertTrue(contextMessage.contains("Late-lock frequency"),
                "Should include late-lock frequency section");
        assertTrue(contextMessage.contains("user-xyz"), "Should include user ID");
        assertTrue(contextMessage.contains("2 out of 4"), "Should include fraction");
    }

    @Test
    void includesReviewTurnaroundSection() {
        ManagerInsightDataProvider.ReviewTurnaroundStats stats =
                new ManagerInsightDataProvider.ReviewTurnaroundStats(2.5, 6);

        ManagerInsightDataProvider.ManagerWeekContext ctx =
                buildContext(List.of(), List.of(), List.of(), stats);

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertTrue(contextMessage.contains("Review turnaround"),
                "Should include review turnaround section");
        assertTrue(contextMessage.contains("2.5"), "Should include avg days");
        assertTrue(contextMessage.contains("6 plans"), "Should include sample size");
    }

    @Test
    void includesAllHistoricalSectionsWhenAllDataPresent() {
        List<ManagerInsightDataProvider.CarryForwardStreak> streaks = List.of(
                new ManagerInsightDataProvider.CarryForwardStreak("u1", 2, List.of("Item A")));
        List<ManagerInsightDataProvider.OutcomeCoverageTrend> trends = List.of(
                new ManagerInsightDataProvider.OutcomeCoverageTrend(
                        "o1", "Revenue", List.of(
                                new ManagerInsightDataProvider.WeeklyCommitCount("2026-03-02", 3),
                                new ManagerInsightDataProvider.WeeklyCommitCount("2026-03-09", 1)
                        )));
        List<ManagerInsightDataProvider.LateLockPattern> lateLocks = List.of(
                new ManagerInsightDataProvider.LateLockPattern("u2", 1, 4));
        ManagerInsightDataProvider.ReviewTurnaroundStats turnaround =
                new ManagerInsightDataProvider.ReviewTurnaroundStats(1.0, 3);

        ManagerInsightDataProvider.ManagerWeekContext ctx =
                buildContext(streaks, trends, lateLocks, turnaround);

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertTrue(contextMessage.contains("Multi-week historical context"));
        assertTrue(contextMessage.contains("Carry-forward streaks"));
        assertTrue(contextMessage.contains("Outcome coverage trends"));
        assertTrue(contextMessage.contains("Late-lock frequency"));
        assertTrue(contextMessage.contains("Review turnaround"));
    }

    @Test
    void omitsHistoricalSectionWhenContextFieldsAreNull() {
        // Simulate a context where historical fields are null (edge-case backward compat)
        ManagerInsightDataProvider.ManagerWeekContext ctx =
                new ManagerInsightDataProvider.ManagerWeekContext(
                        WEEK_START,
                        new ManagerInsightDataProvider.ReviewCounts(0, 0, 0),
                        List.of(),
                        List.of(),
                        null,  // null streaks
                        null,  // null trends
                        null,  // null late-locks
                        null   // null turnaround
                );

        List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(ctx);
        String contextMessage = messages.get(1).content();

        assertFalse(contextMessage.contains("Multi-week historical context"),
                "Historical section should be absent when all historical fields are null");
    }
}
