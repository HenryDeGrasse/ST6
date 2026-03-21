package com.weekly.plan.service;

import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that enforces the plan/commit data retention policy (PRD §14.7).
 *
 * <p>Two-phase deletion:
 * <ol>
 *   <li><b>Soft-delete</b> – plans and commits whose {@code created_at} is older than
 *       {@code weekly.plan.retention.soft-delete-years} are marked with
 *       {@code deleted_at = NOW()}. They disappear from all normal queries immediately
 *       thanks to the {@code @SQLRestriction("deleted_at IS NULL")} on the entities.</li>
 *   <li><b>Hard-delete</b> – plans that were soft-deleted more than
 *       {@code weekly.plan.retention.hard-delete-grace-days} ago are permanently removed
 *       from the database. {@code ON DELETE CASCADE} propagates the deletion to
 *       {@code weekly_commits} and {@code weekly_commit_actuals}.</li>
 * </ol>
 *
 * <p>Enabled via {@code weekly.plan.retention.enabled=true}.
 * Runs daily at 02:00 UTC.
 */
@Component
@ConditionalOnProperty(
        name = "weekly.plan.retention.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PlanRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(PlanRetentionJob.class);

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final int softDeleteYears;
    private final int hardDeleteGraceDays;
    private final Clock clock;

    @Autowired
    public PlanRetentionJob(
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            @Value("${weekly.plan.retention.soft-delete-years:3}") int softDeleteYears,
            @Value("${weekly.plan.retention.hard-delete-grace-days:90}") int hardDeleteGraceDays
    ) {
        this(weeklyPlanRepository, weeklyCommitRepository,
                softDeleteYears, hardDeleteGraceDays, Clock.systemUTC());
    }

    PlanRetentionJob(
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            int softDeleteYears,
            int hardDeleteGraceDays,
            Clock clock
    ) {
        if (softDeleteYears <= 0) {
            throw new IllegalArgumentException(
                    "weekly.plan.retention.soft-delete-years must be greater than 0");
        }
        if (hardDeleteGraceDays <= 0) {
            throw new IllegalArgumentException(
                    "weekly.plan.retention.hard-delete-grace-days must be greater than 0");
        }
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.softDeleteYears = softDeleteYears;
        this.hardDeleteGraceDays = hardDeleteGraceDays;
        this.clock = clock;
    }

    /**
     * Executes both the soft-delete and hard-delete phases.
     * Runs daily at 02:00 UTC.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void runRetention() {
        Instant now = Instant.now(clock);

        softDeleteOldPlans(now);
        hardDeleteExpiredPlans(now);
    }

    /**
     * Marks plans and commits older than {@code softDeleteYears} as soft-deleted.
     */
    private void softDeleteOldPlans(Instant now) {
        Instant softDeleteCutoff = now.minus(softDeleteYears * 365L, ChronoUnit.DAYS);
        int softDeletedCommits = weeklyCommitRepository.softDeleteCommitsBefore(softDeleteCutoff);
        int softDeletedPlans = weeklyPlanRepository.softDeletePlansBefore(softDeleteCutoff);
        if (softDeletedPlans > 0 || softDeletedCommits > 0) {
            LOG.info("Plan retention: soft-deleted {} plan(s) and {} commit(s) older than {} "
                            + "year(s) (cutoff={})",
                    softDeletedPlans, softDeletedCommits, softDeleteYears, softDeleteCutoff);
        } else {
            LOG.debug("Plan retention: no plans or commits to soft-delete (cutoff={})",
                    softDeleteCutoff);
        }
    }

    /**
     * Permanently removes plans that have been soft-deleted for more than
     * {@code hardDeleteGraceDays} days.
     */
    private void hardDeleteExpiredPlans(Instant now) {
        Instant graceCutoff = now.minus(hardDeleteGraceDays, ChronoUnit.DAYS);
        int hardDeleted = weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(graceCutoff);
        if (hardDeleted > 0) {
            LOG.info("Plan retention: hard-deleted {} plan(s) past grace period of {} day(s) "
                    + "(graceCutoff={})", hardDeleted, hardDeleteGraceDays, graceCutoff);
        } else {
            LOG.debug("Plan retention: no soft-deleted plans past grace period (graceCutoff={})",
                    graceCutoff);
        }
    }

    // ── Visible for testing ──────────────────────────────────

    int getSoftDeleteYears() {
        return softDeleteYears;
    }

    int getHardDeleteGraceDays() {
        return hardDeleteGraceDays;
    }
}
