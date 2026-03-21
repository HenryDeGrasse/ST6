package com.weekly.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.WeeklyServiceApplication;
import com.weekly.auth.UserPrincipal;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.OvercommitLevel;
import com.weekly.shared.OvercommitWarning;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for capacity profile computation with real Postgres.
 *
 * <p>Boots the full Spring context against a real Postgres container (via
 * Testcontainers), runs all Flyway migrations, and exercises the complete
 * capacity profile lifecycle:
 *
 * <ol>
 *   <li>Inserts 9 weeks of plan/commit/actual test data directly via the
 *       JPA repositories (bypassing the HTTP layer to keep setup concise).</li>
 *   <li>Calls {@link CapacityProfileService#computeProfile} and asserts:
 *       <ul>
 *         <li>correct {@code avgEstimatedHours} and {@code avgActualHours}</li>
 *         <li>correct {@code estimationBias} ratio (actual / estimated)</li>
 *         <li>correct {@code realisticWeeklyCap} (p50 median of weekly actuals)</li>
 *         <li>{@code categoryBiasJson} contains the expected DELIVERY entry</li>
 *         <li>{@code priorityCompletionJson} contains the expected QUEEN entry</li>
 *         <li>{@code confidenceLevel = HIGH} (9 weeks > 8-week threshold)</li>
 *       </ul>
 *   </li>
 *   <li>Calls {@link CapacityProfileService#getProfile} and verifies the
 *       profile was actually persisted to the database.</li>
 *   <li>Creates a plan whose committed hours exceed the realistic capacity cap
 *       and verifies {@link DefaultCapacityQualityProvider#getOvercommitmentWarning}
 *       returns {@link OvercommitLevel#HIGH}.</li>
 * </ol>
 *
 * <p>Follows the {@link com.weekly.PlanLifecycleIntegrationTest} pattern:
 * {@code @Testcontainers} with a Postgres 16 container, dev auth tokens
 * encoded in the Spring {@link SecurityContextHolder} (rather than HTTP headers),
 * and the {@code init-rls-user.sql} script that creates the non-superuser
 * {@code app_user} so RLS policies are enforced at the database level.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    classes = WeeklyServiceApplication.class,
    properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "ai.provider=stub",
        "notification.materializer.enabled=false",
        "tenant.rls.enabled=true"
    }
)
@ActiveProfiles("test")
class CapacityProfileIntegrationTest {

    /**
     * Postgres 16 container with an init script that creates the non-superuser
     * {@code app_user} role for RLS enforcement.
     *
     * <p>Flyway runs as the superuser ({@code weekly}); the application
     * connects as {@code app_user} so RLS policies are enforced.
     */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("weekly")
                    .withUsername("weekly")
                    .withPassword("weekly")
                    .withInitScript("init-rls-user.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Application connects as app_user so RLS policies are enforced.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_user");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

        // Flyway needs superuser to create tables and RLS policies.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private WeeklyPlanRepository planRepository;

    @Autowired
    private WeeklyCommitRepository commitRepository;

    @Autowired
    private WeeklyCommitActualRepository actualRepository;

    @Autowired
    private CapacityProfileService capacityProfileService;

    @Autowired
    private DefaultCapacityQualityProvider capacityQualityProvider;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test identifiers ──────────────────────────────────────────────────────

    /**
     * Stable org ID used throughout the test; must not conflict with the
     * IDs used by other integration tests sharing the same Postgres container.
     */
    private static final UUID ORG_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000099");

    /**
     * Stable user ID for the capacity profile owner.
     */
    private static final UUID USER_ID =
            UUID.fromString("d0000000-0000-0000-0000-000000000099");

    /**
     * Monday of the current UTC week — matches what {@link CapacityProfileService}
     * computes as {@code windowEnd} when using {@link java.time.Clock#systemUTC()}.
     */
    private static final LocalDate THIS_MONDAY =
            LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);

    // ── Security context lifecycle ────────────────────────────────────────────

    /**
     * Installs a {@link UserPrincipal} in the {@link SecurityContextHolder}
     * before each test so that the {@code TenantRlsTransactionListener} can
     * read the org ID and execute {@code SET LOCAL app.current_org_id} at
     * the start of every JPA transaction.
     */
    @BeforeEach
    void installSecurityContext() {
        UserPrincipal principal = new UserPrincipal(USER_ID, ORG_ID, Set.of("IC"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("IC")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Removes the test security context so it does not leak to other tests. */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Integration test ──────────────────────────────────────────────────────

    /**
     * Full capacity profile lifecycle:
     *
     * <ol>
     *   <li>Insert 9 weeks of plan/commit/actual data — all DELIVERY / QUEEN,
     *       {@code estimatedHours = 10.0}, {@code actualHours = 8.0}, status DONE.</li>
     *   <li>Call {@link CapacityProfileService#computeProfile} and verify computed
     *       metrics:
     *       <ul>
     *         <li>{@code avgEstimatedHours = 10.0}</li>
     *         <li>{@code avgActualHours = 8.0}</li>
     *         <li>{@code estimationBias = 0.80} (8 / 10)</li>
     *         <li>{@code realisticWeeklyCap = 8.0} (p50 of [8,8,…,8])</li>
     *         <li>{@code weeksAnalyzed = 9}</li>
     *         <li>{@code confidenceLevel = "HIGH"} (9 > 8)</li>
     *         <li>{@code categoryBiasJson} DELIVERY entry with correct bias</li>
     *         <li>{@code priorityCompletionJson} QUEEN entry with doneRate 1.0</li>
     *       </ul>
     *   </li>
     *   <li>Call {@link CapacityProfileService#getProfile} and verify the profile
     *       was persisted to and is retrievable from the database.</li>
     *   <li>Create an overcommitted plan (DELIVERY commit, estimatedHours = 15.0)
     *       and verify {@link DefaultCapacityQualityProvider#getOvercommitmentWarning}
     *       returns {@link OvercommitLevel#HIGH}.
     *       Rationale: adjustedTotal = 15.0 × bias(0.80) = 12.0 h, which exceeds
     *       the high threshold of cap(8.0) × 1.2 = 9.6 h.</li>
     * </ol>
     */
    @Test
    void fullCapacityProfileLifecycle() throws Exception {

        // ── Step 1: Insert 9 weeks of historical plan data ────────────────────
        // Each week contributes one DELIVERY / QUEEN commit: est = 10 h, actual = 8 h, DONE.
        // This uniform dataset produces predictable averages and a clean HIGH confidence level.
        for (int weekOffset = 0; weekOffset < 9; weekOffset++) {
            LocalDate weekStart = THIS_MONDAY.minusWeeks(weekOffset);

            WeeklyPlanEntity plan = planRepository.save(
                    new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, weekStart));

            WeeklyCommitEntity commit = commitRepository.save(
                    buildCommit(plan.getId(), CommitCategory.DELIVERY, ChessPriority.QUEEN, "10.0"));

            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setActualResult("Completed");
            actual.setCompletionStatus(CompletionStatus.DONE);
            actual.setActualHours(new BigDecimal("8.0"));
            actualRepository.save(actual);
        }

        // ── Step 2: Compute the capacity profile ──────────────────────────────
        // Rolling window = 12 weeks; only 9 plans exist → weeksAnalyzed = 9.
        CapacityProfileEntity profile =
                capacityProfileService.computeProfile(ORG_ID, USER_ID, 12);

        // ── Step 3: Assert top-level profile metrics ──────────────────────────
        assertThat(profile.getConfidenceLevel())
                .as("confidenceLevel must be HIGH when weeksAnalyzed > 8")
                .isEqualTo("HIGH");

        assertThat(profile.getWeeksAnalyzed())
                .as("weeksAnalyzed must equal the number of plans found in the window")
                .isEqualTo(9);

        assertThat(profile.getAvgEstimatedHours())
                .as("avgEstimatedHours = mean(9 × 10 h) = 10.0")
                .isEqualByComparingTo(new BigDecimal("10.0"));

        assertThat(profile.getAvgActualHours())
                .as("avgActualHours = mean(9 × 8 h) = 8.0")
                .isEqualByComparingTo(new BigDecimal("8.0"));

        assertThat(profile.getEstimationBias())
                .as("estimationBias = avgActual / avgEstimated = 8.0 / 10.0 = 0.80")
                .isEqualByComparingTo(new BigDecimal("0.80"));

        assertThat(profile.getRealisticWeeklyCap())
                .as("realisticWeeklyCap = p50 of [8,8,8,8,8,8,8,8,8] = 8.0")
                .isEqualByComparingTo(new BigDecimal("8.0"));

        assertThat(profile.getComputedAt())
                .as("computedAt must be populated")
                .isNotNull();

        // ── Step 4: Assert categoryBiasJson DELIVERY entry ────────────────────
        List<Map<String, Object>> categoryBias =
                objectMapper.readValue(profile.getCategoryBiasJson(), new TypeReference<>() {});

        assertThat(categoryBias)
                .as("categoryBiasJson must contain at least one entry")
                .isNotEmpty();

        Map<String, Object> deliveryEntry = categoryBias.stream()
                .filter(e -> "DELIVERY".equals(e.get("category")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected DELIVERY entry in categoryBiasJson but was not found; "
                                + "got: " + profile.getCategoryBiasJson()));

        assertThat(((Number) deliveryEntry.get("avgEstimatedHours")).doubleValue())
                .as("DELIVERY avgEstimatedHours should be 10.0")
                .isCloseTo(10.0, within(0.01));

        assertThat(((Number) deliveryEntry.get("avgActualHours")).doubleValue())
                .as("DELIVERY avgActualHours should be 8.0")
                .isCloseTo(8.0, within(0.01));

        assertThat(((Number) deliveryEntry.get("bias")).doubleValue())
                .as("DELIVERY bias = avgActual(8.0) / avgEstimated(10.0) = 0.80")
                .isCloseTo(0.80, within(0.001));

        // ── Step 5: Assert priorityCompletionJson QUEEN entry ─────────────────
        List<Map<String, Object>> priorityCompletion =
                objectMapper.readValue(profile.getPriorityCompletionJson(), new TypeReference<>() {});

        assertThat(priorityCompletion)
                .as("priorityCompletionJson must contain at least one entry")
                .isNotEmpty();

        Map<String, Object> queenEntry = priorityCompletion.stream()
                .filter(e -> "QUEEN".equals(e.get("priority")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected QUEEN entry in priorityCompletionJson but was not found; "
                                + "got: " + profile.getPriorityCompletionJson()));

        assertThat(((Number) queenEntry.get("doneRate")).doubleValue())
                .as("QUEEN doneRate must be 1.0 (all 9 commits completed DONE)")
                .isCloseTo(1.0, within(0.001));

        assertThat(((Number) queenEntry.get("sampleSize")).intValue())
                .as("QUEEN sampleSize must equal the number of QUEEN commits inserted (9)")
                .isEqualTo(9);

        assertThat(((Number) queenEntry.get("avgHours")).doubleValue())
                .as("QUEEN avgHours = mean of 9 × 8 h actual = 8.0")
                .isCloseTo(8.0, within(0.01));

        // ── Step 6: Verify the profile is persisted and retrievable ───────────
        // getProfile() opens a new read-only transaction and reads from the DB;
        // if the profile was not committed in step 2 this call would return empty.
        Optional<CapacityProfileEntity> persisted =
                capacityProfileService.getProfile(ORG_ID, USER_ID);

        assertThat(persisted)
                .as("getProfile() must return the profile saved by computeProfile()")
                .isPresent();

        assertThat(persisted.get().getConfidenceLevel())
                .isEqualTo("HIGH");

        assertThat(persisted.get().getWeeksAnalyzed())
                .isEqualTo(9);

        assertThat(persisted.get().getAvgEstimatedHours())
                .isEqualByComparingTo(new BigDecimal("10.0"));

        assertThat(persisted.get().getAvgActualHours())
                .isEqualByComparingTo(new BigDecimal("8.0"));

        assertThat(persisted.get().getEstimationBias())
                .isEqualByComparingTo(new BigDecimal("0.80"));

        assertThat(persisted.get().getRealisticWeeklyCap())
                .isEqualByComparingTo(new BigDecimal("8.0"));

        // ── Step 7: Create a plan that exceeds capacity ───────────────────────
        // estimatedHours = 15.0 h with DELIVERY category:
        //   categoryBias[DELIVERY] = 0.80
        //   adjustedTotal = 15.0 × 0.80 = 12.0 h
        //   highThreshold  = realisticCap(8.0) × 1.2 = 9.6 h
        //   12.0 > 9.6 → OvercommitLevel.HIGH
        //
        // Use THIS_MONDAY.plusWeeks(1) to avoid the unique (org_id, user_id, week_start_date)
        // constraint conflict with the week-0 historical plan created above.
        LocalDate nextWeekMonday = THIS_MONDAY.plusWeeks(1);
        WeeklyPlanEntity overcommittedPlan = planRepository.save(
                new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, nextWeekMonday));

        commitRepository.save(
                buildCommit(overcommittedPlan.getId(),
                        CommitCategory.DELIVERY, ChessPriority.QUEEN, "15.0"));

        // ── Step 8: Verify DefaultCapacityQualityProvider detects HIGH overcommit
        Optional<OvercommitWarning> warning = capacityQualityProvider
                .getOvercommitmentWarning(ORG_ID, overcommittedPlan.getId(), USER_ID);

        assertThat(warning)
                .as("OvercommitWarning must be present — capacity profile exists for this user")
                .isPresent();

        assertThat(warning.get().level())
                .as("Adjusted hours (12.0 h) > high threshold (9.6 h) → OvercommitLevel.HIGH")
                .isEqualTo(OvercommitLevel.HIGH);

        assertThat(warning.get().realisticCap())
                .as("realisticCap in warning must match the computed profile cap")
                .isEqualByComparingTo(new BigDecimal("8.0"));

        assertThat(warning.get().adjustedTotal())
                .as("adjustedTotal = 15.0 × bias(0.80) = 12.0 h")
                .isEqualByComparingTo(new BigDecimal("12.0"));

        assertThat(warning.get().message())
                .as("Warning message must be non-empty for HIGH overcommit")
                .isNotBlank();
    }

    // ── Factory helpers ────────────────────────────────────────────────────────

    /**
     * Constructs (but does not save) a {@link WeeklyCommitEntity} with the
     * given plan association, category, priority, and estimated hours.
     *
     * @param planId         the owning plan ID
     * @param category       commitment category for bias tracking
     * @param priority       chess priority for completion-rate tracking
     * @param estimatedHours effort estimate in hours
     * @return an unsaved commit entity
     */
    private WeeklyCommitEntity buildCommit(
            UUID planId,
            CommitCategory category,
            ChessPriority priority,
            String estimatedHours) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Capacity integration test commit");
        commit.setCategory(category);
        commit.setChessPriority(priority);
        commit.setEstimatedHours(new BigDecimal(estimatedHours));
        return commit;
    }
}
