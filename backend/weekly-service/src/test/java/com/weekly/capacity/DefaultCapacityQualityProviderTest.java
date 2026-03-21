package com.weekly.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.OvercommitLevel;
import com.weekly.shared.OvercommitWarning;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultCapacityQualityProvider}.
 */
class DefaultCapacityQualityProviderTest {

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private CapacityProfileService profileService;
    private OvercommitDetector overcommitDetector;
    private DefaultCapacityQualityProvider capacityQualityProvider;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        profileService = mock(CapacityProfileService.class);
        overcommitDetector = mock(OvercommitDetector.class);
        capacityQualityProvider = new DefaultCapacityQualityProvider(
                planRepository, commitRepository, profileService, overcommitDetector);
    }

    @Test
    void returnsEmptyWhenPlanDoesNotExist() {
        UUID orgId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(planRepository.findByOrgIdAndId(orgId, planId)).thenReturn(Optional.empty());

        Optional<OvercommitWarning> result =
                capacityQualityProvider.getOvercommitmentWarning(orgId, planId, userId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(commitRepository, profileService, overcommitDetector);
    }

    @Test
    void returnsEmptyWhenUserDoesNotOwnPlan() {
        UUID orgId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID requesterUserId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, orgId, ownerUserId, LocalDate.now());

        when(planRepository.findByOrgIdAndId(orgId, planId)).thenReturn(Optional.of(plan));

        Optional<OvercommitWarning> result =
                capacityQualityProvider.getOvercommitmentWarning(orgId, planId, requesterUserId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(commitRepository, profileService, overcommitDetector);
    }

    @Test
    void returnsEmptyWhenNoCapacityProfileExists() {
        UUID orgId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, orgId, userId, LocalDate.now());

        when(planRepository.findByOrgIdAndId(orgId, planId)).thenReturn(Optional.of(plan));
        when(commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId)).thenReturn(List.of());
        when(profileService.getProfile(orgId, userId)).thenReturn(Optional.empty());

        Optional<OvercommitWarning> result =
                capacityQualityProvider.getOvercommitmentWarning(orgId, planId, userId);

        assertTrue(result.isEmpty());
        verify(planRepository).findByOrgIdAndId(orgId, planId);
        verify(commitRepository).findByOrgIdAndWeeklyPlanId(orgId, planId);
        verify(profileService).getProfile(orgId, userId);
        verifyNoInteractions(overcommitDetector);
    }

    @Test
    void delegatesToDetectorWhenPlanAndProfileExist() {
        UUID orgId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, orgId, userId, LocalDate.now());
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), orgId, planId, "Commit");
        CapacityProfileEntity profile = new CapacityProfileEntity(orgId, userId);
        profile.setWeeksAnalyzed(6);
        profile.setRealisticWeeklyCap(new BigDecimal("18.0"));

        OvercommitWarning warning = new OvercommitWarning(
                OvercommitLevel.MODERATE,
                "Heads up",
                new BigDecimal("20.0"),
                new BigDecimal("18.0"));

        when(planRepository.findByOrgIdAndId(orgId, planId)).thenReturn(Optional.of(plan));
        when(commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId)).thenReturn(List.of(commit));
        when(profileService.getProfile(orgId, userId)).thenReturn(Optional.of(profile));
        when(overcommitDetector.detectOvercommitment(List.of(commit), profile)).thenReturn(warning);

        Optional<OvercommitWarning> result =
                capacityQualityProvider.getOvercommitmentWarning(orgId, planId, userId);

        assertTrue(result.isPresent());
        assertEquals(warning, result.get());
        verify(overcommitDetector).detectOvercommitment(List.of(commit), profile);
    }
}
