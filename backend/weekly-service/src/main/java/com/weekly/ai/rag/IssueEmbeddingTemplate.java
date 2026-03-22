package com.weekly.ai.rag;

import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renders the structured text input used to embed an issue into the vector store.
 *
 * <p>The template follows the Phase 6 plan §7 format, combining the issue's
 * structured fields with a summary of recent activity entries and reconciliation
 * history. The resulting text is passed to {@link EmbeddingClient#embed(String)}.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Activity summary is capped at the latest 5 entries.</li>
 *   <li>Reconciliation history is capped at the latest 4 entries.</li>
 *   <li>{@code ChessPriority} (from {@code plan.domain}) is intentionally omitted to keep
 *       the {@code ai.rag} package free of {@code plan.domain} dependencies
 *       (enforced by {@code ModuleBoundaryTest}).</li>
 * </ul>
 */
@Component
public class IssueEmbeddingTemplate {

    private static final int MAX_ACTIVITIES = 5;
    private static final int MAX_RECONCILIATIONS = 4;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    /**
     * Backward-compatible overload used by existing tests/callers.
     */
    public String render(IssueEntity issue, List<IssueActivityEntity> activities) {
        return render(issue, null, null, activities, List.of());
    }

    /**
     * Renders a structured text representation of the issue and its related context.
     *
     * @param issue the issue to embed
     * @param teamName display team name, if available
     * @param outcomeContext resolved outcome/rally-cry/objective context, if available
     * @param activities full activity log for the issue
     * @param reconciliationHistory recent weekly reconciliation outcomes for the issue
     * @return trimmed, multi-line string ready for the embedding model
     */
    public String render(
            IssueEntity issue,
            String teamName,
            OutcomeContext outcomeContext,
            List<IssueActivityEntity> activities,
            List<ReconciliationHistoryEntry> reconciliationHistory
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("Issue: ")
                .append(issue.getIssueKey())
                .append(" — ")
                .append(issue.getTitle())
                .append('\n');

        if (teamName != null && !teamName.isBlank()) {
            sb.append("Team: ").append(teamName).append('\n');
        }

        String outcomeLine = renderOutcomeLine(outcomeContext);
        if (outcomeLine != null) {
            sb.append("Outcome: ").append(outcomeLine).append('\n');
        }

        String effortLine = renderEffortLine(issue);
        if (effortLine != null) {
            sb.append("Effort: ").append(effortLine).append('\n');
        }

        sb.append("Status: ").append(issue.getStatus().name()).append('\n');

        if (issue.getDescription() != null && !issue.getDescription().isBlank()) {
            sb.append("Description: ").append(issue.getDescription()).append('\n');
        }

        List<String> activitySummary = activities.stream()
                .filter(a -> a.getCommentText() != null
                        || a.getActivityType().name().contains("CHANGE"))
                .sorted(Comparator.comparing(IssueActivityEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(MAX_ACTIVITIES)
                .map(IssueEmbeddingTemplate::summariseActivity)
                .collect(Collectors.toList());

        if (!activitySummary.isEmpty()) {
            sb.append('\n').append("Activity summary:").append('\n');
            for (String line : activitySummary) {
                sb.append("- ").append(line).append('\n');
            }
        }

        List<ReconciliationHistoryEntry> reconciliations = reconciliationHistory.stream()
                .sorted(Comparator.comparing(ReconciliationHistoryEntry::weekLabel,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .limit(MAX_RECONCILIATIONS)
                .toList();

        if (!reconciliations.isEmpty()) {
            sb.append('\n').append("Reconciliation history:").append('\n');
            for (ReconciliationHistoryEntry entry : reconciliations) {
                sb.append("- ").append(summariseReconciliation(entry)).append('\n');
            }
        }

        return sb.toString().trim();
    }

    private static String renderOutcomeLine(OutcomeContext outcomeContext) {
        if (outcomeContext == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (outcomeContext.rallyCryName() != null && !outcomeContext.rallyCryName().isBlank()) {
            parts.add(outcomeContext.rallyCryName());
        }
        if (outcomeContext.objectiveName() != null && !outcomeContext.objectiveName().isBlank()) {
            parts.add(outcomeContext.objectiveName());
        }
        if (outcomeContext.outcomeName() != null && !outcomeContext.outcomeName().isBlank()) {
            parts.add(outcomeContext.outcomeName());
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private static String renderEffortLine(IssueEntity issue) {
        List<String> parts = new ArrayList<>();
        if (issue.getEffortType() != null) {
            parts.add(issue.getEffortType().name());
        }
        if (issue.getEstimatedHours() != null) {
            parts.add(issue.getEstimatedHours().toPlainString() + "h estimated");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String summariseActivity(IssueActivityEntity activity) {
        String date = renderDate(activity.getCreatedAt());
        if (activity.getCommentText() != null) {
            return String.format(
                    "\"%s\" — %s, %s",
                    activity.getCommentText(),
                    activity.getActorUserId(),
                    date
            );
        }

        String oldVal = activity.getOldValue() != null ? activity.getOldValue() : "(none)";
        String newVal = activity.getNewValue() != null ? activity.getNewValue() : "(none)";
        return String.format(
                "%s: %s %s → %s",
                date,
                activity.getActivityType().name(),
                oldVal,
                newVal
        );
    }

    private static String summariseReconciliation(ReconciliationHistoryEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("Week of ")
                .append(entry.weekLabel())
                .append(": ")
                .append(entry.completionStatus());
        if (entry.hoursSpent() != null) {
            sb.append(", ")
                    .append(entry.hoursSpent().toPlainString())
                    .append("h spent");
        }
        if (entry.actualResult() != null && !entry.actualResult().isBlank()) {
            sb.append(". \"")
                    .append(entry.actualResult())
                    .append("\"");
        }
        return sb.toString();
    }

    private static String renderDate(Instant instant) {
        return instant == null ? "unknown-date" : DATE_FORMAT.format(instant);
    }

    /** Resolved outcome display context for embedding text. */
    public record OutcomeContext(
            String rallyCryName,
            String objectiveName,
            String outcomeName
    ) {}

    /** Reconciliation history entry rendered into the embedding document. */
    public record ReconciliationHistoryEntry(
            String weekLabel,
            String completionStatus,
            BigDecimal hoursSpent,
            String actualResult
    ) {}
}
