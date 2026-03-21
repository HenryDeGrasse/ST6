package com.weekly.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ApiErrorResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link EstimationCoachingController}.
 */
class EstimationCoachingControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 16);

    private AuthenticatedUserContext authenticatedUserContext;
    private CapacityProfileService capacityProfileService;
    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private WeeklyCommitActualRepository weeklyCommitActualRepository;
    private EstimationCoachingController controller;

    @BeforeEach
    void setUp() {
        authenticatedUserContext = new AuthenticatedUserContext();
        capacityProfileService = mock(CapacityProfileService.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        weeklyCommitActualRepository = mock(WeeklyCommitActualRepository.class);
        controller = new EstimationCoachingController(
                authenticatedUserContext,
                capacityProfileService,
                weeklyPlanRepository,
                weeklyCommitRepository,
                weeklyCommitActualRepository,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getEstimationCoachingReturnsNotFoundWhenPlanIsNotOwnedByCaller() {
        loginAsUser(USER_ID);
        UUID planId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, OTHER_USER_ID, WEEK_START);
        when(weeklyPlanRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

        ResponseEntity<?> response = controller.getEstimationCoaching(planId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("NOT_FOUND", error.error().code());
        verifyNoInteractions(capacityProfileService, weeklyCommitRepository, weeklyCommitActualRepository);
    }

    @Test
    void getEstimationCoachingReturnsComputedSummaryAndHistoricalInsights() {
        loginAsUser(USER_ID);
        UUID planId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, WEEK_START);
        WeeklyCommitEntity firstCommit = commit(planId, CommitCategory.DELIVERY, ChessPriority.KING, "4.0");
        WeeklyCommitEntity secondCommit = commit(planId, CommitCategory.OPERATIONS, ChessPriority.QUEEN, "6.0");
        WeeklyCommitActualEntity firstActual = actual(firstCommit.getId(), "5.0");
        WeeklyCommitActualEntity secondActual = actual(secondCommit.getId(), "3.0");
        CapacityProfileEntity profile = new CapacityProfileEntity(ORG_ID, USER_ID);
        profile.setEstimationBias(new BigDecimal("1.25"));
        profile.setConfidenceLevel("HIGH");
        profile.setCategoryBiasJson(
                "["
                        + "{\"category\":\"DELIVERY\",\"bias\":1.30},"
                        + "{\"category\":\"OPERATIONS\",\"bias\":0.80}"
                        + "]");
        profile.setPriorityCompletionJson(
                "["
                        + "{\"priority\":\"KING\",\"doneRate\":0.75,\"sampleSize\":4},"
                        + "{\"priority\":\"QUEEN\",\"doneRate\":0.50,\"sampleSize\":2}"
                        + "]");

        when(weeklyPlanRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                .thenReturn(List.of(firstCommit, secondCommit));
        when(weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(
                ORG_ID, List.of(firstCommit.getId(), secondCommit.getId())))
                .thenReturn(List.of(firstActual, secondActual));
        when(capacityProfileService.getProfile(ORG_ID, USER_ID)).thenReturn(Optional.of(profile));

        ResponseEntity<?> response = controller.getEstimationCoaching(planId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        EstimationCoachingResponse body =
                assertInstanceOf(EstimationCoachingResponse.class, response.getBody());
        assertEquals(10.0, body.thisWeekEstimated());
        assertEquals(8.0, body.thisWeekActual());
        assertEquals(0.8, body.accuracyRatio());
        assertEquals(1.25, body.overallBias());
        assertEquals("HIGH", body.confidenceLevel());
        assertEquals(2, body.categoryInsights().size());
        assertEquals("DELIVERY", body.categoryInsights().getFirst().category());
        assertEquals(1.3, body.categoryInsights().getFirst().bias());
        assertEquals(
                "Consider adding 30% buffer to DELIVERY estimates.",
                body.categoryInsights().getFirst().tip());
        assertEquals("OPERATIONS", body.categoryInsights().get(1).category());
        assertEquals(
                "You tend to overestimate OPERATIONS tasks; consider reducing estimates by ~20%.",
                body.categoryInsights().get(1).tip());
        assertEquals(2, body.priorityInsights().size());
        assertEquals("KING", body.priorityInsights().getFirst().priority());
        assertEquals(0.75, body.priorityInsights().getFirst().completionRate());
        assertEquals(4, body.priorityInsights().getFirst().sampleSize());
    }

    @Test
    void getEstimationCoachingDefaultsConfidenceAndPreservesNullCategoryBias() {
        loginAsUser(USER_ID);
        UUID planId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, WEEK_START);
        WeeklyCommitEntity commit = commit(planId, CommitCategory.DELIVERY, ChessPriority.KING, "0.0");
        CapacityProfileEntity profile = new CapacityProfileEntity(ORG_ID, USER_ID);
        profile.setConfidenceLevel(null);
        profile.setCategoryBiasJson("[{\"category\":\"DELIVERY\",\"bias\":null}]");
        profile.setPriorityCompletionJson("[]");

        when(weeklyPlanRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId)).thenReturn(List.of(commit));
        when(weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(commit.getId())))
                .thenReturn(List.of());
        when(capacityProfileService.getProfile(ORG_ID, USER_ID)).thenReturn(Optional.of(profile));

        ResponseEntity<?> response = controller.getEstimationCoaching(planId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        EstimationCoachingResponse body =
                assertInstanceOf(EstimationCoachingResponse.class, response.getBody());
        assertNull(body.accuracyRatio());
        assertEquals("LOW", body.confidenceLevel());
        assertEquals(1, body.categoryInsights().size());
        CategoryInsight insight = body.categoryInsights().getFirst();
        assertEquals("DELIVERY", insight.category());
        assertNull(insight.bias());
        assertNull(insight.tip());
        assertEquals(List.of(), body.priorityInsights());
    }

    private static WeeklyCommitEntity commit(
            UUID planId,
            CommitCategory category,
            ChessPriority priority,
            String estimatedHours) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Commit");
        commit.setCategory(category);
        commit.setChessPriority(priority);
        commit.setEstimatedHours(new BigDecimal(estimatedHours));
        return commit;
    }

    private static WeeklyCommitActualEntity actual(UUID commitId, String actualHours) {
        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commitId, ORG_ID);
        actual.setActualHours(new BigDecimal(actualHours));
        return actual;
    }

    private static void loginAsUser(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(userId, ORG_ID, Set.of("INDIVIDUAL_CONTRIBUTOR")),
                        null,
                        List.of()));
    }
}
