package com.weekly.usermodel;

import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that recomputes user model snapshots for all active users across
 * all organisations.
 *
 * <p>Runs every Sunday at 2 AM UTC to refresh the rolling-window user model
 * snapshot used by the user profile API.
 *
 * <p>The computation window is fixed at {@value #ROLLING_WEEKS} weeks. Snapshots
 * are upserted via {@link UserModelService#computeSnapshot}.
 *
 * <p>Enabled via {@code weekly.usermodel.compute.enabled=true} (worker profile only).
 * Defaults to {@code false} so the job is never active in local / test environments.
 */
@Component
@ConditionalOnProperty(name = "weekly.usermodel.compute.enabled", havingValue = "true",
        matchIfMissing = false)
public class UserModelComputeJob {

    private static final Logger LOG = LoggerFactory.getLogger(UserModelComputeJob.class);

    /** Number of rolling weeks used when computing each snapshot. */
    static final int ROLLING_WEEKS = 12;

    private final UserModelService userModelService;
    private final WeeklyPlanRepository planRepository;
    private final Clock clock;

    @Autowired
    public UserModelComputeJob(
            UserModelService userModelService,
            WeeklyPlanRepository planRepository) {
        this(userModelService, planRepository, Clock.systemUTC());
    }

    /** Package-private constructor for unit tests (injectable clock). */
    UserModelComputeJob(
            UserModelService userModelService,
            WeeklyPlanRepository planRepository,
            Clock clock) {
        this.userModelService = userModelService;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    /**
     * Recomputes user model snapshots for every active user in every organisation.
     *
     * <p>"Active" is defined as having at least one plan in the last
     * {@value #ROLLING_WEEKS} weeks. One failure for a single user does not
     * abort the remaining recomputations.
     *
     * <p>Scheduled for Sunday at 2 AM UTC so fresh snapshots are ready before
     * the Monday planning week begins.
     */
    @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
    public void computeAllSnapshots() {
        List<UUID> orgIds = planRepository.findDistinctOrgIds();

        if (orgIds.isEmpty()) {
            LOG.debug("UserModelComputeJob: no orgs with plans found, skipping");
            return;
        }

        LOG.info("UserModelComputeJob: starting snapshot computation for {} org(s)", orgIds.size());

        LocalDate windowEnd = LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate windowStart = windowEnd.minusWeeks(ROLLING_WEEKS - 1);

        int totalSnapshots = 0;
        int failedSnapshots = 0;

        for (UUID orgId : orgIds) {
            try {
                int[] counts = computeOrgSnapshots(orgId, windowStart, windowEnd);
                totalSnapshots += counts[0];
                failedSnapshots += counts[1];
            } catch (Exception e) {
                LOG.warn("UserModelComputeJob: error processing org {}: {}", orgId, e.getMessage(), e);
            }
        }

        LOG.info("UserModelComputeJob: computation complete — {} snapshot(s) computed, {} failed",
                totalSnapshots, failedSnapshots);
    }

    private int[] computeOrgSnapshots(UUID orgId, LocalDate windowStart, LocalDate windowEnd) {
        List<WeeklyPlanEntity> recentPlans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);

        List<UUID> userIds = recentPlans.stream()
                .map(WeeklyPlanEntity::getOwnerUserId)
                .distinct()
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            LOG.debug("UserModelComputeJob: no active users in org {}, skipping", orgId);
            return new int[]{0, 0};
        }

        LOG.debug("UserModelComputeJob: computing {} snapshot(s) for org {}", userIds.size(), orgId);

        int computed = 0;
        int failed = 0;

        for (UUID userId : userIds) {
            try {
                userModelService.computeSnapshot(orgId, userId, ROLLING_WEEKS);
                computed++;
            } catch (Exception e) {
                LOG.warn(
                        "UserModelComputeJob: failed to compute snapshot for user {} in org {}: {}",
                        userId, orgId, e.getMessage(), e);
                failed++;
            }
        }

        return new int[]{computed, failed};
    }
}
