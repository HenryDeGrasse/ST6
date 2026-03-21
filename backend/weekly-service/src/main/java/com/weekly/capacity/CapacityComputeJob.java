package com.weekly.capacity;

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
 * Scheduled job that recomputes capacity profiles for all active users across
 * all organisations.
 *
 * <p>Runs every Sunday at 2 AM UTC to refresh the rolling-window capacity
 * profile used by the overcommit detector and the team capacity view.
 *
 * <p>The computation window is fixed at 12 weeks (configurable by changing the
 * constant {@code ROLLING_WEEKS}). Profiles are upserted via
 * {@link CapacityProfileService#computeProfile}.
 *
 * <p>Enabled via {@code capacity.compute.enabled=true} (worker profile only).
 * Defaults to {@code false} in the base configuration so the job is never
 * active in local / test environments.
 */
@Component
@ConditionalOnProperty(name = "capacity.compute.enabled", havingValue = "true")
public class CapacityComputeJob {

    private static final Logger LOG = LoggerFactory.getLogger(CapacityComputeJob.class);

    /** Number of rolling weeks used when computing each profile. */
    static final int ROLLING_WEEKS = 12;

    private final CapacityProfileService capacityProfileService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final Clock clock;

    @Autowired
    public CapacityComputeJob(
            CapacityProfileService capacityProfileService,
            WeeklyPlanRepository weeklyPlanRepository) {
        this(capacityProfileService, weeklyPlanRepository, Clock.systemUTC());
    }

    /** Package-private constructor for unit tests (injectable clock). */
    CapacityComputeJob(
            CapacityProfileService capacityProfileService,
            WeeklyPlanRepository weeklyPlanRepository,
            Clock clock) {
        this.capacityProfileService = capacityProfileService;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.clock = clock;
    }

    /**
     * Recomputes capacity profiles for every active user in every organisation.
     *
     * <p>"Active" is defined as having at least one plan in the last
     * {@value #ROLLING_WEEKS} weeks. One failure for a single user does not
     * abort the remaining recomputations.
     *
     * <p>Scheduled for Sunday at 2 AM UTC so fresh profiles are ready before
     * the Monday planning week begins.
     */
    @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
    public void recomputeAllProfiles() {
        List<UUID> orgIds = weeklyPlanRepository.findDistinctOrgIds();

        if (orgIds.isEmpty()) {
            LOG.debug("CapacityComputeJob: no orgs with plans found, skipping");
            return;
        }

        LOG.info("CapacityComputeJob: starting profile recomputation for {} org(s)", orgIds.size());

        LocalDate windowEnd = LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate windowStart = windowEnd.minusWeeks(ROLLING_WEEKS - 1);

        int totalProfiles = 0;

        for (UUID orgId : orgIds) {
            try {
                totalProfiles += recomputeOrgProfiles(orgId, windowStart, windowEnd);
            } catch (Exception e) {
                LOG.warn("CapacityComputeJob: error processing org {}: {}", orgId, e.getMessage(), e);
            }
        }

        LOG.info("CapacityComputeJob: recomputation complete — {} profile(s) computed", totalProfiles);
    }

    private int recomputeOrgProfiles(UUID orgId, LocalDate windowStart, LocalDate windowEnd) {
        // Find all plans within the rolling window for this org
        List<WeeklyPlanEntity> recentPlans =
                weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);

        // Extract unique user IDs from those plans
        List<UUID> userIds = recentPlans.stream()
                .map(WeeklyPlanEntity::getOwnerUserId)
                .distinct()
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            LOG.debug("CapacityComputeJob: no active users in org {}, skipping", orgId);
            return 0;
        }

        LOG.debug("CapacityComputeJob: recomputing {} profile(s) for org {}",
                userIds.size(), orgId);

        int computedProfiles = 0;
        for (UUID userId : userIds) {
            try {
                capacityProfileService.computeProfile(orgId, userId, ROLLING_WEEKS);
                computedProfiles++;
            } catch (Exception e) {
                LOG.warn("CapacityComputeJob: failed to compute profile for user {} in org {}: {}",
                        userId, orgId, e.getMessage(), e);
            }
        }

        return computedProfiles;
    }
}
