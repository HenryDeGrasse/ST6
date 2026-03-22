package com.weekly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekly.ai.rag.IssueEmbeddingTemplate;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueActivityType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.plan.domain.ChessPriority;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link IssueEmbeddingTemplate}.
 */
class IssueEmbeddingTemplateTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();

    private IssueEmbeddingTemplate template;

    @BeforeEach
    void setUp() {
        template = new IssueEmbeddingTemplate();
    }

    @Test
    void renderIncludesIssueKeyAndTitle() {
        IssueEntity issue = issue("PLAT-42", "Add dark mode support");

        String result = template.render(issue, List.of());

        assertThat(result).contains("PLAT-42").contains("Add dark mode support");
    }

    @Test
    void renderIncludesTeamAndOutcomeContextWhenProvided() {
        IssueEntity issue = issue("PLAT-1", "Improve onboarding");

        String result = template.render(
                issue,
                "Platform",
                new IssueEmbeddingTemplate.OutcomeContext(
                        "Revenue growth",
                        "Increase activation",
                        "Improve onboarding conversion"
                ),
                List.of(),
                List.of()
        );

        assertThat(result).contains("Team: Platform");
        assertThat(result).contains("Outcome: Revenue growth / Increase activation / Improve onboarding conversion");
    }

    @Test
    void renderIncludesStatus() {
        IssueEntity issue = issue("PLAT-2", "Some issue");
        issue.setStatus(IssueStatus.IN_PROGRESS);

        String result = template.render(issue, List.of());

        assertThat(result).contains("Status: IN_PROGRESS");
    }

    @Test
    void renderIncludesEffortTypeAndEstimatedHoursWhenPresent() {
        IssueEntity issue = issue("PLAT-3", "Build a thing");
        issue.setEffortType(EffortType.BUILD);
        issue.setEstimatedHours(new BigDecimal("4.50"));

        String result = template.render(issue, List.of());

        assertThat(result).contains("Effort: BUILD, 4.50h estimated");
    }

    @Test
    void renderOmitsEffortLineWhenBothFieldsMissing() {
        IssueEntity issue = issue("PLAT-4", "No effort fields");

        String result = template.render(issue, List.of());

        assertThat(result).doesNotContain("Effort:");
    }

    @Test
    void renderDoesNotIncludeChessPriorityToPreserveModuleBoundary() {
        IssueEntity issue = issue("PLAT-5", "Critical thing");
        issue.setChessPriority(ChessPriority.KING);

        String result = template.render(issue, List.of());

        assertThat(result).doesNotContain("Priority:");
        assertThat(result).doesNotContain("KING");
    }

    @Test
    void renderIncludesDescriptionWhenNonBlank() {
        IssueEntity issue = issue("PLAT-6", "Described task");
        issue.setDescription("This is a detailed description of the task.");

        String result = template.render(issue, List.of());

        assertThat(result).contains("Description: This is a detailed description of the task.");
    }

    @Test
    void renderOmitsDescriptionWhenBlank() {
        IssueEntity issue = issue("PLAT-7", "No description");
        issue.setDescription("");

        String result = template.render(issue, List.of());

        assertThat(result).doesNotContain("Description:");
    }

    @Test
    void renderIncludesCommentActivitiesWithActorAndDate() {
        IssueEntity issue = issue("PLAT-8", "Commented task");
        IssueActivityEntity comment = activity(issue.getId(), IssueActivityType.COMMENT, "2026-03-20T10:15:30Z");
        comment.withComment("This needs to be split into two sub-tasks.");

        String result = template.render(issue, List.of(comment));

        assertThat(result).contains("Activity summary:");
        assertThat(result).contains("\"This needs to be split into two sub-tasks.\"");
        assertThat(result).contains(CREATOR_ID.toString());
        assertThat(result).contains("2026-03-20");
    }

    @Test
    void renderIncludesFieldChangeActivities() {
        IssueEntity issue = issue("PLAT-9", "Changed task");
        IssueActivityEntity change = activity(issue.getId(), IssueActivityType.STATUS_CHANGE, "2026-03-21T10:15:30Z");
        change.withChange("OPEN", "IN_PROGRESS");

        String result = template.render(issue, List.of(change));

        assertThat(result).contains("Activity summary:");
        assertThat(result).contains("STATUS_CHANGE");
        assertThat(result).contains("OPEN → IN_PROGRESS");
    }

    @Test
    void renderUsesLatestFiveActivities() {
        IssueEntity issue = issue("PLAT-10", "Many activities");
        List<IssueActivityEntity> activities = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            IssueActivityEntity activity = activity(
                    issue.getId(),
                    IssueActivityType.COMMENT,
                    String.format("2026-03-%02dT10:15:30Z", i + 10)
            );
            activity.withComment("Comment " + i);
            activities.add(activity);
        }

        String result = template.render(issue, activities);

        assertThat(result).contains("Comment 5");
        assertThat(result).contains("Comment 1");
        assertThat(result).doesNotContain("Comment 0");
    }

    @Test
    void renderIncludesReconciliationHistoryWhenProvided() {
        IssueEntity issue = issue("PLAT-11", "Reconciled task");

        String result = template.render(
                issue,
                "Platform",
                null,
                List.of(),
                List.of(new IssueEmbeddingTemplate.ReconciliationHistoryEntry(
                        "2026-03-17",
                        "DONE",
                        new BigDecimal("3.50"),
                        "Completed rollout to staging"
                ))
        );

        assertThat(result).contains("Reconciliation history:");
        assertThat(result).contains("Week of 2026-03-17: DONE, 3.50h spent. \"Completed rollout to staging\"");
    }

    @Test
    void renderNoActivitiesProducesNoActivitiesSection() {
        IssueEntity issue = issue("PLAT-12", "No activities");

        String result = template.render(issue, List.of());

        assertThat(result).doesNotContain("Activity summary:");
    }

    @Test
    void renderResultIsTrimmed() {
        IssueEntity issue = issue("PLAT-13", "Trim test");

        String result = template.render(issue, List.of());

        assertThat(result).doesNotStartWith(" ");
        assertThat(result).doesNotEndWith("\n");
    }

    private static IssueEntity issue(String key, String title) {
        int seq = Integer.parseInt(key.split("-")[1]);
        return new IssueEntity(UUID.randomUUID(), ORG_ID, TEAM_ID, key, seq, title, CREATOR_ID);
    }

    private static IssueActivityEntity activity(UUID issueId, IssueActivityType type, String createdAt) {
        IssueActivityEntity activity = new IssueActivityEntity(
                UUID.randomUUID(), ORG_ID, issueId, CREATOR_ID, type);
        ReflectionTestUtils.setField(activity, "createdAt", Instant.parse(createdAt));
        return activity;
    }
}
