package com.weekly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the rapid-fire batch check-in (Quick Update) endpoint.
 *
 * <p>Boots the full Spring context against a real Postgres container, runs
 * Flyway migrations, and exercises the POST /api/v1/plans/{planId}/quick-update
 * endpoint end-to-end through the controller layer using MockMvc.
 *
 * <p>Covers:
 * <ul>
 *   <li>Successful batch check-in on a LOCKED plan with persistence verification</li>
 *   <li>Rejection of a DRAFT plan (confirms {@link com.weekly.quickupdate.QuickUpdateExceptionHandler}
 *       returns 409 CONFLICT, not a 500 fall-through)</li>
 *   <li>Rejection of an unknown plan (confirms 404 NOT_FOUND, not 500)</li>
 *   <li>Rejection of a mismatched commit ID (confirms 404 NOT_FOUND, not 500)</li>
 * </ul>
 *
 * <p>Uses the dev auth token format ({@code Bearer dev:<userId>:<orgId>:<roles>})
 * so {@code DevRequestAuthenticator} is active.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = WeeklyServiceApplication.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "ai.provider=stub",
        "notification.materializer.enabled=false",
        "tenant.rls.enabled=true"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class QuickUpdateIntegrationTest {

    /**
     * Postgres container with an init script that creates a non-superuser
     * {@code app_user} for RLS enforcement. Flyway migrations run as the
     * superuser ({@code weekly}); the application connects as {@code app_user}
     * so RLS policies are enforced.
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
        // App connects as app_user so RLS policies are enforced
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_user");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

        // Flyway needs superuser to create tables and RLS policies
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test identifiers ─────────────────────────────────────

    private static final UUID ORG_1 = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    /** Dev token for org 1 / user 1. */
    private static final String TOKEN_ORG1 =
            "Bearer dev:" + USER_1 + ":" + ORG_1 + ":IC";

    /**
     * Truncates all transactional tables between tests via a direct superuser
     * JDBC connection that bypasses RLS policies. Children are deleted before
     * parents to respect foreign-key constraints.
     */
    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM progress_entries");
            stmt.execute("DELETE FROM user_update_patterns");
            stmt.execute("DELETE FROM user_model_snapshots");
            stmt.execute("DELETE FROM weekly_commit_actuals");
            stmt.execute("DELETE FROM manager_reviews");
            stmt.execute("DELETE FROM weekly_commits");
            stmt.execute("DELETE FROM weekly_plans");
            stmt.execute("DELETE FROM audit_events");
            stmt.execute("DELETE FROM outbox_events");
            stmt.execute("DELETE FROM idempotency_keys");
            stmt.execute("DELETE FROM notifications");
        }
    }

    // ── Test 1: Successful batch check-in on a locked plan ───

    /**
     * POST create plan → POST create 2 commits → POST lock →
     * POST quick-update with 2 items → assert 200, updatedCount=2, entries.length=2.
     * GET /commits/{commitId}/check-ins → verify persistence of each entry.
     */
    @Test
    void batchCheckInOnLockedPlan() throws Exception {
        LocalDate weekStart = currentMonday();

        // ── 1. Create a plan ──────────────────────────────────
        MvcResult createPlanResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plans", weekStart)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn();

        JsonNode planJson = objectMapper.readTree(
                createPlanResult.getResponse().getContentAsString());
        String planId = planJson.get("id").asText();

        // ── 2. Create a KING commit (non-strategic reason avoids RCDO dependency) ──
        String kingBody = objectMapper.writeValueAsString(new CommitRequest(
                "Ship Planning APIs", null, "KING", "DELIVERY",
                null, "Strategic delivery for internal platform", "All APIs live", 0.9, null
        ));
        MvcResult kingResult = mockMvc.perform(
                        post("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kingBody))
                .andExpect(status().isCreated())
                .andReturn();

        String kingCommitId = objectMapper.readTree(
                kingResult.getResponse().getContentAsString()).get("id").asText();

        // ── 3. Create a QUEEN commit ──────────────────────────
        String queenBody = objectMapper.writeValueAsString(new CommitRequest(
                "Fix CI Pipeline", null, "QUEEN", "TECH_DEBT",
                null, "Infrastructure maintenance", "Green builds", 0.8, null
        ));
        MvcResult queenResult = mockMvc.perform(
                        post("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(queenBody))
                .andExpect(status().isCreated())
                .andReturn();

        String queenCommitId = objectMapper.readTree(
                queenResult.getResponse().getContentAsString()).get("id").asText();

        // ── 4. Lock the plan ──────────────────────────────────
        int planVersion = fetchPlanVersion(planId);
        mockMvc.perform(
                        post("/api/v1/plans/{planId}/lock", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .header("If-Match", planVersion)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LOCKED"));

        // ── 5. POST quick-update with 2 items ─────────────────
        String quickUpdateBody = objectMapper.writeValueAsString(new QuickUpdateRequest(List.of(
                new QuickUpdateItem(UUID.fromString(kingCommitId), "ON_TRACK",
                        "Making good progress on APIs"),
                new QuickUpdateItem(UUID.fromString(queenCommitId), "DONE_EARLY",
                        "CI fixed ahead of schedule")
        )));

        MvcResult quickUpdateResult = mockMvc.perform(
                        post("/api/v1/plans/{planId}/quick-update", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quickUpdateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andReturn();

        JsonNode quickUpdateJson = objectMapper.readTree(
                quickUpdateResult.getResponse().getContentAsString());
        assertThat(quickUpdateJson.get("updatedCount").asInt()).isEqualTo(2);
        assertThat(quickUpdateJson.get("entries")).hasSize(2);

        // ── 6. Verify persistence: GET /commits/{commitId}/check-ins ──
        mockMvc.perform(
                        get("/api/v1/commits/{commitId}/check-ins", kingCommitId)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitId").value(kingCommitId))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].status").value("ON_TRACK"))
                .andExpect(jsonPath("$.entries[0].note").value("Making good progress on APIs"));

        mockMvc.perform(
                        get("/api/v1/commits/{commitId}/check-ins", queenCommitId)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitId").value(queenCommitId))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].status").value("DONE_EARLY"))
                .andExpect(jsonPath("$.entries[0].note").value("CI fixed ahead of schedule"));
    }

    // ── Test 2: Reject quick-update on a DRAFT plan ───────────

    /**
     * Creates a plan that stays in DRAFT, then attempts a quick-update.
     * Expects 409 CONFLICT with ErrorCode=CONFLICT and a planState detail,
     * confirming {@link com.weekly.quickupdate.QuickUpdateExceptionHandler}
     * handles the {@link com.weekly.plan.service.PlanStateException} rather
     * than the global catch-all producing a 500.
     */
    @Test
    void rejectsDraftPlan() throws Exception {
        LocalDate weekStart = currentMonday();

        // Create plan — leave it in DRAFT
        MvcResult createPlanResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plans", weekStart)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn();

        String planId = objectMapper.readTree(
                createPlanResult.getResponse().getContentAsString()).get("id").asText();

        // Attempt quick-update on DRAFT plan
        String quickUpdateBody = objectMapper.writeValueAsString(new QuickUpdateRequest(List.of(
                new QuickUpdateItem(UUID.randomUUID(), "ON_TRACK", null)
        )));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/plans/{planId}/quick-update", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quickUpdateBody))
                .andExpect(status().isConflict())
                .andReturn();

        // Verify error body: ErrorCode=CONFLICT and planState=DRAFT in details
        JsonNode errorJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(errorJson.get("error").get("code").asText()).isEqualTo("CONFLICT");
        String detailsString = errorJson.get("error").get("details").toString();
        assertThat(detailsString).contains("planState");
        assertThat(detailsString).contains("DRAFT");
    }

    // ── Test 3: Reject quick-update for unknown plan ──────────

    /**
     * Attempts a quick-update for a random (non-existent) plan ID.
     * Expects 404 NOT_FOUND with ErrorCode=NOT_FOUND, confirming
     * {@link com.weekly.quickupdate.QuickUpdateExceptionHandler}
     * handles {@link com.weekly.plan.service.PlanNotFoundException}.
     */
    @Test
    void rejectsUnknownPlan() throws Exception {
        UUID randomPlanId = UUID.randomUUID();

        String quickUpdateBody = objectMapper.writeValueAsString(new QuickUpdateRequest(List.of(
                new QuickUpdateItem(UUID.randomUUID(), "ON_TRACK", null)
        )));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/plans/{planId}/quick-update", randomPlanId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quickUpdateBody))
                .andExpect(status().isNotFound())
                .andReturn();

        // Verify error body has ErrorCode NOT_FOUND
        JsonNode errorJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(errorJson.get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    }

    // ── Test 4: Reject quick-update with mismatched commit ID ─

    /**
     * Creates and locks a plan, then attempts a quick-update with a commit ID
     * that does not belong to the plan. Expects 404 NOT_FOUND with
     * ErrorCode=NOT_FOUND, confirming
     * {@link com.weekly.quickupdate.QuickUpdateExceptionHandler}
     * handles {@link com.weekly.plan.service.CommitNotFoundException}.
     */
    @Test
    void rejectsMismatchedCommitId() throws Exception {
        LocalDate weekStart = currentMonday();

        // ── 1. Create a plan ──────────────────────────────────
        MvcResult createPlanResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plans", weekStart)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isCreated())
                .andReturn();

        String planId = objectMapper.readTree(
                createPlanResult.getResponse().getContentAsString()).get("id").asText();

        // ── 2. Create a KING commit so the plan can be locked ─
        String kingBody = objectMapper.writeValueAsString(new CommitRequest(
                "Ship Planning APIs", null, "KING", "DELIVERY",
                null, "Strategic delivery for internal platform", "All APIs live", 0.9, null
        ));
        mockMvc.perform(
                        post("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kingBody))
                .andExpect(status().isCreated());

        // ── 3. Lock the plan ──────────────────────────────────
        int planVersion = fetchPlanVersion(planId);
        mockMvc.perform(
                        post("/api/v1/plans/{planId}/lock", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .header("If-Match", planVersion)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LOCKED"));

        // ── 4. Quick-update with a commit ID not in this plan ─
        UUID wrongCommitId = UUID.randomUUID();
        String quickUpdateBody = objectMapper.writeValueAsString(new QuickUpdateRequest(List.of(
                new QuickUpdateItem(wrongCommitId, "ON_TRACK", null)
        )));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/plans/{planId}/quick-update", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quickUpdateBody))
                .andExpect(status().isNotFound())
                .andReturn();

        // Verify error body has ErrorCode NOT_FOUND
        JsonNode errorJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(errorJson.get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    }

    // ── Helpers ──────────────────────────────────────────────

    private static LocalDate currentMonday() {
        LocalDate today = LocalDate.now();
        return today.with(DayOfWeek.MONDAY);
    }

    private int fetchPlanVersion(String planId) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/plans/{planId}", planId)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("version").asInt();
    }

    // ── Request DTOs for JSON serialization ──────────────────

    private record CommitRequest(
            String title,
            String description,
            String chessPriority,
            String category,
            String outcomeId,
            String nonStrategicReason,
            String expectedResult,
            Double confidence,
            String[] tags
    ) {}

    private record QuickUpdateRequest(
            List<QuickUpdateItem> updates
    ) {}

    private record QuickUpdateItem(
            UUID commitId,
            String status,
            String note
    ) {}
}
