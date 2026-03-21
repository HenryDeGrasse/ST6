package com.weekly.capacity;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityQualityProvider;
import com.weekly.shared.OvercommitWarning;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default capacity-module implementation of {@link CapacityQualityProvider}.
 *
 * <p>Loads the plan, its commits, and the owner's capacity profile, then
 * delegates the overcommitment evaluation to {@link OvercommitDetector}.
 *
 * <p>Returns {@link Optional#empty()} when:
 * <ul>
 *   <li>the plan does not exist in the org, or</li>
 *   <li>the requesting user is not the plan owner, or</li>
 *   <li>no capacity profile has been computed yet for the user.</li>
 * </ul>
 */
@Service
public class DefaultCapacityQualityProvider implements CapacityQualityProvider {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final CapacityProfileService profileService;
    private final OvercommitDetector overcommitDetector;

    public DefaultCapacityQualityProvider(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            CapacityProfileService profileService,
            OvercommitDetector overcommitDetector) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.profileService = profileService;
        this.overcommitDetector = overcommitDetector;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The implementation is read-only; no state is mutated.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<OvercommitWarning> getOvercommitmentWarning(
            UUID orgId, UUID planId, UUID userId) {

        // 1. Load and authorise plan
        Optional<WeeklyPlanEntity> planOpt = planRepository.findByOrgIdAndId(orgId, planId);
        if (planOpt.isEmpty() || !userId.equals(planOpt.get().getOwnerUserId())) {
            return Optional.empty();
        }

        // 2. Load commits for this plan
        List<WeeklyCommitEntity> commits =
                commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);

        // 3. Load capacity profile; empty = no historical data
        Optional<CapacityProfileEntity> profileOpt =
                profileService.getProfile(orgId, userId);
        if (profileOpt.isEmpty()) {
            return Optional.empty();
        }

        // 4. Delegate detection
        OvercommitWarning warning =
                overcommitDetector.detectOvercommitment(commits, profileOpt.get());
        return Optional.of(warning);
    }
}
