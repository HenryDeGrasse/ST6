package com.weekly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
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
 * Full-stack integration test that boots the Spring context against a real
 * Postgres container, runs Flyway migrations, and exercises the complete
 * plan lifecycle through the controller layer using MockMvc.
 *
 * <p>This single test covers:
 * <ul>
 *   <li>Flyway migration correctness against real Postgres</li>
 *   <li>JPA entity mapping under real Postgres (arrays, enums, timestamps)</li>
 *   <li>Full plan lifecycle: DRAFT → LOCKED → RECONCILING → RECONCILED</li>
 *   <li>RCDO snapshot population at lock time</li>
 *   <li>RLS tenant isolation (via a non-superuser app role)</li>
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
class PlanLifecycleIntegrationTest {

    /**
     * Postgres container with an init script that creates a non-superuser
     * {@code app_user} for RLS enforcement. The Flyway migrations run as the
     * superuser ({@code weekly}), then the application connects as
     * {@code app_user} so RLS policies are enforced.
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
        // Flyway runs as superuser; app connects as app_user for RLS
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

    @Autowired
    private InMemoryRcdoClient rcdoClient;

    @Autowired
    private DataSource dataSource;

    // ── Test identifiers ────────────────────────────────────

    private static final UUID ORG_1 = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID ORG_2 = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_2 = UUID.fromString("c0000000-0000-0000-0000-000000000002");

    private static final String OUTCOME_ID = "e0000000-0000-0000-0000-000000000001";

    /** Dev token for org 1 / user 1. */
    private static final String TOKEN_ORG1 =
            "Bearer dev:" + USER_1 + ":" + ORG_1 + ":IC";

    /** Dev token for org 2 / user 2. */
    private static final String TOKEN_ORG2 =
            "Bearer dev:" + USER_2 + ":" + ORG_2 + ":IC";

    @BeforeEach
    void seedRcdo() {
        rcdoClient.clear();

        String rc1 = "10000000-0000-0000-0000-000000000001";
        String obj1 = "20000000-0000-0000-0000-000000000001";

        RcdoTree tree = new RcdoTree(List.of(
                new RcdoTree.RallyCry(rc1, "Scale to $500M ARR", List.of(
                        new RcdoTree.Objective(obj1, "Accelerate enterprise pipeline", rc1, List.of(
                                new RcdoTree.Outcome(OUTCOME_ID, "Close 10 enterprise deals", obj1)
                        ))
                ))
        ));

        rcdoClient.setTree(ORG_1, tree);
        rcdoClient.setTree(ORG_2, tree);
    }

    /**
     * Exercises the full plan lifecycle: create → add commits → lock →
     * start reconciliation → save actuals → submit reconciliation.
     * Also verifies RCDO snapshot population and RLS tenant isolation.
     */
    @Test
    void fullPlanLifecycle() throws Exception {
        LocalDate weekStart = currentMonday();

        // ── 1. Create a plan ─────────────────────────────────
        MvcResult createResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn();

        JsonNode planJson = objectMapper.readTree(
                createResult.getResponse().getContentAsString());
        String planId = planJson.get("id").asText();
        int planVersion = planJson.get("version").asInt(); // may be bumped by commit creation

        // ── 2. Add a KING commit linked to an RCDO outcome ──
        String kingBody = objectMapper.writeValueAsString(new CommitRequest(
                "Ship planning APIs", null, "KING", "DELIVERY",
                OUTCOME_ID, null, "All APIs live", 0.9, null
        ));
        MvcResult kingResult = mockMvc.perform(
                        post("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kingBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kingJson = objectMapper.readTree(
                kingResult.getResponse().getContentAsString());
        String kingCommitId = kingJson.get("id").asText();

        // ── 3. Add a QUEEN commit (non-strategic) ───────────
        String queenBody = objectMapper.writeValueAsString(new CommitRequest(
                "Fix CI pipeline", null, "QUEEN", "TECH_DEBT",
                null, "Infrastructure maintenance", "Green builds", 0.8, null
        ));
        MvcResult queenResult = mockMvc.perform(
                        post("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(queenBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode queenJson = objectMapper.readTree(
                queenResult.getResponse().getContentAsString());
        String queenCommitId = queenJson.get("id").asText();

        // Re-fetch plan to get current version (commit creation bumps plan version)
        MvcResult planAfterCommits = mockMvc.perform(
                        get("/api/v1/plans/{planId}", planId)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andReturn();
        planVersion = objectMapper.readTree(
                planAfterCommits.getResponse().getContentAsString()).get("version").asInt();

        // ── 4. Lock the plan ────────────────────────────────
        mockMvc.perform(
                        post("/api/v1/plans/{planId}/lock", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .header("If-Match", planVersion)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LOCKED"));

        // Verify RCDO snapshot was populated on the KING commit
        MvcResult commitsResult = mockMvc.perform(
                        get("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode commitsJson = objectMapper.readTree(
                commitsResult.getResponse().getContentAsString());
        JsonNode kingCommitNode = findCommitById(commitsJson, kingCommitId);
        assertThat(kingCommitNode).isNotNull();
        assertThat(kingCommitNode.get("snapshotOutcomeId").asText()).isEqualTo(OUTCOME_ID);
        assertThat(kingCommitNode.get("snapshotOutcomeName").asText())
                .isEqualTo("Close 10 enterprise deals");
        assertThat(kingCommitNode.get("snapshotRallyCryName").asText())
                .isEqualTo("Scale to $500M ARR");

        // ── 5. Start reconciliation ─────────────────────────
        // Re-fetch plan to get current version after lock
        int currentVersion = fetchPlanVersion(planId);
        mockMvc.perform(
                        post("/api/v1/plans/{planId}/start-reconciliation", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .header("If-Match", currentVersion)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECONCILING"));

        // ── 6. Save actuals for each commit ─────────────────
        // Fetch current commit versions for If-Match
        MvcResult commitsAfterRecon = mockMvc.perform(
                        get("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode commitsAfterReconJson = objectMapper.readTree(
                commitsAfterRecon.getResponse().getContentAsString());

        JsonNode kingAfterRecon = findCommitById(commitsAfterReconJson, kingCommitId);
        int kingVersion = kingAfterRecon.get("version").asInt();

        JsonNode queenAfterRecon = findCommitById(commitsAfterReconJson, queenCommitId);
        int queenVersion = queenAfterRecon.get("version").asInt();

        // KING commit: DONE
        String kingActualBody = objectMapper.writeValueAsString(new ActualRequest(
                "All planning APIs shipped and documented", "DONE", null, 40
        ));
        mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", kingCommitId)
                        .header("Authorization", TOKEN_ORG1)
                        .header("If-Match", kingVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kingActualBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionStatus").value("DONE"));

        // QUEEN commit: PARTIALLY with delta reason
        String queenActualBody = objectMapper.writeValueAsString(new ActualRequest(
                "Fixed 3 of 5 pipeline issues", "PARTIALLY",
                "Remaining issues require infra team support", 16
        ));
        mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", queenCommitId)
                        .header("Authorization", TOKEN_ORG1)
                        .header("If-Match", queenVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queenActualBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionStatus").value("PARTIALLY"));

        // ── 7. Submit reconciliation ─────────────────────────
        // Re-fetch plan version (actuals bump commit version, not plan version)
        int preSubmitVersion = fetchPlanVersion(planId);
        mockMvc.perform(
                        post("/api/v1/plans/{planId}/submit-reconciliation", planId)
                                .header("Authorization", TOKEN_ORG1)
                                .header("If-Match", preSubmitVersion)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECONCILED"));

        // ── 8. Verify final plan state ──────────────────────
        mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECONCILED"))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEW_PENDING"));

        // ── 9. RLS isolation: org 2's plan is invisible to org 1 ──
        // Create a plan for org 2
        MvcResult org2PlanResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plans", weekStart)
                                .header("Authorization", TOKEN_ORG2))
                .andExpect(status().isCreated())
                .andReturn();

        String org2PlanId = objectMapper.readTree(
                org2PlanResult.getResponse().getContentAsString()).get("id").asText();

        // Org 1 cannot see org 2's plan via the controller layer.
        mockMvc.perform(get("/api/v1/plans/{planId}", org2PlanId)
                        .header("Authorization", TOKEN_ORG1))
                .andExpect(status().isNotFound());

        // Org 2 can see its own plan.
        mockMvc.perform(get("/api/v1/plans/{planId}", org2PlanId)
                        .header("Authorization", TOKEN_ORG2))
                .andExpect(status().isOk());

        // The database-level RLS policy also hides org 2's row from org 1
        // even without an org_id predicate in the query.
        assertThat(countPlansVisibleToOrg(ORG_1, org2PlanId)).isZero();
        assertThat(countPlansVisibleToOrg(ORG_2, org2PlanId)).isEqualTo(1);
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

    private int countPlansVisibleToOrg(UUID orgId, String planId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement setTenant = connection.prepareStatement(
                    "SET LOCAL app.current_org_id = '" + orgId + "'"
            )) {
                setTenant.execute();
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT count(*) FROM weekly_plans WHERE id = ?::uuid"
            )) {
                query.setString(1, planId);
                try (ResultSet resultSet = query.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private JsonNode findCommitById(JsonNode commitsArray, String commitId) {
        for (JsonNode node : commitsArray) {
            if (commitId.equals(node.get("id").asText())) {
                return node;
            }
        }
        return null;
    }

    // ── Request DTOs for JSON serialization ─────────────────

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

    private record ActualRequest(
            String actualResult,
            String completionStatus,
            String deltaReason,
            Integer timeSpent
    ) {}
}
