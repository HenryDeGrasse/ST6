package com.weekly.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.UserPrincipal;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.CapacityQualityProvider;
import com.weekly.shared.OvercommitLevel;
import com.weekly.shared.OvercommitWarning;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Unit tests for {@link CapacityController}.
 */
class CapacityControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TEAM_MEMBER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 16);

    private AuthenticatedUserContext authenticatedUserContext;
    private CapacityProfileService capacityProfileService;
    private CapacityQualityProvider capacityQualityProvider;
    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private OrgGraphClient orgGraphClient;
    private OvercommitDetector overcommitDetector;
    private CapacityController controller;

    @BeforeEach
    void setUp() {
        authenticatedUserContext = new AuthenticatedUserContext();
        capacityProfileService = mock(CapacityProfileService.class);
        capacityQualityProvider = mock(CapacityQualityProvider.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        orgGraphClient = mock(OrgGraphClient.class);
        overcommitDetector = mock(OvercommitDetector.class);
        controller = new CapacityController(
                authenticatedUserContext,
                capacityProfileService,
                capacityQualityProvider,
                weeklyPlanRepository,
                weeklyCommitRepository,
                orgGraphClient,
                overcommitDetector);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyCapacityReturnsNotFoundWhenProfileDoesNotExist() {
        loginAsManager();
        when(capacityProfileService.getProfile(ORG_ID, MANAGER_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getMyCapacity();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("NOT_FOUND", error.error().code());
    }

    @Test
    void getMyCapacityReturnsMappedProfileWhenPresent() {
        loginAsManager();
        CapacityProfileEntity profile = new CapacityProfileEntity(ORG_ID, MANAGER_ID);
        profile.setWeeksAnalyzed(6);
        profile.setAvgEstimatedHours(new BigDecimal("22.5"));
        profile.setAvgActualHours(new BigDecimal("24.0"));
        profile.setEstimationBias(new BigDecimal("1.07"));
        profile.setRealisticWeeklyCap(new BigDecimal("25.0"));
        profile.setCategoryBiasJson("[]");
        profile.setPriorityCompletionJson("[]");
        profile.setConfidenceLevel("MEDIUM");
        profile.setComputedAt(Instant.parse("2026-03-20T10:15:30Z"));
        when(capacityProfileService.getProfile(ORG_ID, MANAGER_ID)).thenReturn(Optional.of(profile));

        ResponseEntity<?> response = controller.getMyCapacity();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CapacityProfileResponse body = assertInstanceOf(CapacityProfileResponse.class, response.getBody());
        assertEquals(ORG_ID.toString(), body.orgId());
        assertEquals(MANAGER_ID.toString(), body.userId());
        assertEquals(new BigDecimal("25.0"), body.realisticWeeklyCap());
        assertEquals("2026-03-20T10:15:30Z", body.computedAt());
    }

    @Test
    void getTeamCapacityReturnsForbiddenForNonManager() {
        loginAsIndividualContributor();

        ResponseEntity<?> response = controller.getTeamCapacity(WEEK_START.toString());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("FORBIDDEN", error.error().code());
        verifyNoInteractions(
                capacityProfileService,
                capacityQualityProvider,
                weeklyPlanRepository,
                weeklyCommitRepository,
                orgGraphClient,
                overcommitDetector);
    }

    @Test
    void getTeamCapacityFallsBackToRawEstimateWhenNoUsableProfileExists() {
        loginAsManager();
        DirectReport report = new DirectReport(TEAM_MEMBER_ID, "Ada Lovelace");
        WeeklyPlanEntity plan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, TEAM_MEMBER_ID, WEEK_START);
        WeeklyCommitEntity firstCommit = commit(plan.getId(), "5.0");
        WeeklyCommitEntity secondCommit = commit(plan.getId(), "3.5");

        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(List.of(report));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                ORG_ID, WEEK_START, List.of(TEAM_MEMBER_ID)))
                .thenReturn(List.of(plan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(plan.getId())))
                .thenReturn(List.of(firstCommit, secondCommit));
        when(capacityProfileService.getProfile(ORG_ID, TEAM_MEMBER_ID)).thenReturn(Optional.empty());
        when(overcommitDetector.detectOvercommitment(List.of(firstCommit, secondCommit), null))
                .thenReturn(new OvercommitWarning(
                        OvercommitLevel.NONE,
                        "",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));

        ResponseEntity<?> response = controller.getTeamCapacity(WEEK_START.toString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TeamCapacityResponse body = assertInstanceOf(TeamCapacityResponse.class, response.getBody());
        assertNotNull(body);
        assertEquals(WEEK_START.toString(), body.weekStart());
        assertEquals(1, body.members().size());
        TeamMemberCapacity member = body.members().getFirst();
        assertEquals(TEAM_MEMBER_ID.toString(), member.userId());
        assertEquals("Ada Lovelace", member.name());
        assertEquals(new BigDecimal("8.5"), member.estimatedHours());
        assertEquals(new BigDecimal("8.5"), member.adjustedEstimate());
        assertEquals("NONE", member.overcommitLevel());
    }

    @Test
    void getTeamCapacityUsesDetectorAdjustedTotalWhenProfileIsUsable() {
        loginAsManager();
        DirectReport report = new DirectReport(TEAM_MEMBER_ID, "Ada Lovelace");
        WeeklyPlanEntity plan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, TEAM_MEMBER_ID, WEEK_START);
        WeeklyCommitEntity commit = commit(plan.getId(), "10.0");
        CapacityProfileEntity profile = new CapacityProfileEntity(ORG_ID, TEAM_MEMBER_ID);
        profile.setWeeksAnalyzed(6);
        profile.setRealisticWeeklyCap(new BigDecimal("12.0"));

        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(List.of(report));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                ORG_ID, WEEK_START, List.of(TEAM_MEMBER_ID)))
                .thenReturn(List.of(plan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(plan.getId())))
                .thenReturn(List.of(commit));
        when(capacityProfileService.getProfile(ORG_ID, TEAM_MEMBER_ID)).thenReturn(Optional.of(profile));
        when(overcommitDetector.detectOvercommitment(List.of(commit), profile))
                .thenReturn(new OvercommitWarning(
                        OvercommitLevel.MODERATE,
                        "Heads up",
                        new BigDecimal("13.0"),
                        new BigDecimal("12.0")));

        ResponseEntity<?> response = controller.getTeamCapacity(WEEK_START.toString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TeamCapacityResponse body = assertInstanceOf(TeamCapacityResponse.class, response.getBody());
        TeamMemberCapacity member = body.members().getFirst();
        assertEquals(new BigDecimal("10.0"), member.estimatedHours());
        assertEquals(new BigDecimal("13.0"), member.adjustedEstimate());
        assertEquals(new BigDecimal("12.0"), member.realisticCap());
        assertEquals("MODERATE", member.overcommitLevel());
    }

    private static WeeklyCommitEntity commit(UUID planId, String estimatedHours) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Commit");
        commit.setCategory(CommitCategory.DELIVERY);
        commit.setEstimatedHours(new BigDecimal(estimatedHours));
        return commit;
    }

    private static void loginAsManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(MANAGER_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()));
    }

    private static void loginAsIndividualContributor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(MANAGER_ID, ORG_ID, Set.of("INDIVIDUAL_CONTRIBUTOR")),
                        null,
                        List.of()));
    }
}
