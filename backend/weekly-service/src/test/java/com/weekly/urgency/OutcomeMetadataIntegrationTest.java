package com.weekly.urgency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.WeeklyServiceApplication;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.math.BigDecimal;
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
 * Full-stack integration test for the outcome metadata CRUD and urgency endpoints.
 *
 * <p>Boots the Spring context against a real Postgres container (via Testcontainers),
 * runs Flyway migrations, and exercises all metadata/urgency scenarios through the
 * controller layer using MockMvc with dev auth tokens.
 *
 * <p>Follows {@link com.weekly.PlanLifecycleIntegrationTest} patterns:
 * {@code @Testcontainers}, {@code @SpringBootTest} with real Postgres, MockMvc,
 * dev auth tokens, and an {@code init-rls-user.sql} initialisation script.
 *
 * <p>Test scenarios covered:
 * <ol>
 *   <li>PUT /api/v1/outcomes/{id}/metadata — admin creates new metadata (200)</li>
 *   <li>PUT same outcome again — updates existing metadata (200 with updated fields)</li>
 *   <li>GET /api/v1/outcomes/metadata — lists all metadata for the org (array)</li>
 *   <li>GET /api/v1/outcomes/{id}/metadata — returns single metadata record (200)</li>
 *   <li>GET /api/v1/outcomes/{id}/metadata for non-existent outcome — returns 404</li>
 *   <li>PATCH /api/v1/outcomes/{id}/progress — updates currentValue and recomputes
 *       progressPct</li>
 *   <li>PUT with IC role (not admin/manager) — returns 403</li>
 *   <li>GET /api/v1/outcomes/urgency-summary — returns urgency summary with outcomes
 *       array</li>
 *   <li>GET /api/v1/team/strategic-slack — returns slack info with band and floor</li>
 * </ol>
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
class OutcomeMetadataIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryRcdoClient rcdoClient;

    // ── Test identifiers ─────────────────────────────────────────────────────

    private static final UUID ORG_1 =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_1 =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");

    /**
     * Outcome registered in the in-memory RCDO tree so that urgency-summary
     * results include a resolved {@code outcomeName}.
     */
    private static final UUID OUTCOME_ID =
            UUID.fromString("e0000000-0000-0000-0000-000000000010");

    /** Dev token for an ADMIN user in org 1. */
    private static final String TOKEN_ADMIN =
            "Bearer dev:" + USER_1 + ":" + ORG_1 + ":ADMIN";

    /** Dev token for an IC (non-privileged) user in org 1. */
    private static final String TOKEN_IC =
            "Bearer dev:" + USER_1 + ":" + ORG_1 + ":IC";

    @BeforeEach
    void seedRcdo() {
        rcdoClient.clear();

        String rc1 = "10000000-0000-0000-0000-000000000001";
        String obj1 = "20000000-0000-0000-0000-000000000001";

        RcdoTree tree = new RcdoTree(List.of(
                new RcdoTree.RallyCry(rc1, "Scale to $500M ARR", List.of(
                        new RcdoTree.Objective(obj1, "Accelerate enterprise pipeline", rc1, List.of(
                                new RcdoTree.Outcome(
                                        OUTCOME_ID.toString(),
                                        "Close 10 enterprise deals",
                                        obj1
                                )
                        ))
                ))
        ));

        rcdoClient.setTree(ORG_1, tree);
    }

    /**
     * Exercises all nine metadata CRUD and urgency scenarios in a single
     * sequential test, mirroring the lifecycle style of
     * {@link com.weekly.PlanLifecycleIntegrationTest#fullPlanLifecycle}.
     *
     * <p>The scenarios are ordered so that each step builds on the state created
     * by the preceding step, reflecting real-world usage patterns.
     */
    @Test
    void fullMetadataCrudAndUrgencyLifecycle() throws Exception {

        // ── Scenario 7: IC role is rejected before any metadata is created ────
        mockMvc.perform(
                        put("/api/v1/outcomes/{id}/metadata", OUTCOME_ID)
                                .header("Authorization", TOKEN_IC)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(metricRequestBody(
                                        "Revenue", 100.0, 40.0, "%", "2099-12-31")))
                .andExpect(status().isForbidden());

        // ── Scenario 1: Admin creates new metadata (200) ─────────────────────
        MvcResult createResult = mockMvc.perform(
                        put("/api/v1/outcomes/{id}/metadata", OUTCOME_ID)
                                .header("Authorization", TOKEN_ADMIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(metricRequestBody(
                                        "Revenue", 100.0, 40.0, "%", "2099-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(ORG_1.toString()))
                .andExpect(jsonPath("$.outcomeId").value(OUTCOME_ID.toString()))
                .andExpect(jsonPath("$.targetDate").value("2099-12-31"))
                .andExpect(jsonPath("$.progressType").value("METRIC"))
                .andExpect(jsonPath("$.metricName").value("Revenue"))
                .andExpect(jsonPath("$.targetValue").isNumber())
                .andExpect(jsonPath("$.currentValue").isNumber())
                .andExpect(jsonPath("$.unit").value("%"))
                .andExpect(jsonPath("$.urgencyBand").isNotEmpty())
                .andExpect(jsonPath("$.progressPct").isNumber())
                .andReturn();

        JsonNode created = objectMapper.readTree(
                createResult.getResponse().getContentAsString());
        assertThat(created.get("outcomeId").asText()).isEqualTo(OUTCOME_ID.toString());
        // Entity just created today; expectedProgress ≈ 0 and actualProgress > 0
        // because METRIC score = 40/100 = 0.40. gap = 0 − 0.24 < 0.10 → ON_TRACK.
        assertThat(created.get("urgencyBand").asText()).isEqualTo("ON_TRACK");
        assertThat(new BigDecimal(created.get("progressPct").asText()))
                .isGreaterThan(BigDecimal.ZERO);

        // ── Scenario 2: PUT same outcome again — updates existing record ──────
        mockMvc.perform(
                        put("/api/v1/outcomes/{id}/metadata", OUTCOME_ID)
                                .header("Authorization", TOKEN_ADMIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(metricRequestBody(
                                        "ARR Growth", 200.0, 80.0, "USD", "2099-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomeId").value(OUTCOME_ID.toString()))
                .andExpect(jsonPath("$.metricName").value("ARR Growth"))
                .andExpect(jsonPath("$.targetValue").isNumber())
                .andExpect(jsonPath("$.currentValue").isNumber())
                .andExpect(jsonPath("$.unit").value("USD"));

        // ── Scenario 3: GET all metadata for org returns list with our entry ─
        mockMvc.perform(
                        get("/api/v1/outcomes/metadata")
                                .header("Authorization", TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(
                        jsonPath("$[?(@.outcomeId == '" + OUTCOME_ID + "')]").exists());

        // ── Scenario 4: GET single outcome metadata returns correct record ────
        mockMvc.perform(
                        get("/api/v1/outcomes/{id}/metadata", OUTCOME_ID)
                                .header("Authorization", TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomeId").value(OUTCOME_ID.toString()))
                .andExpect(jsonPath("$.metricName").value("ARR Growth"));

        // ── Scenario 5: GET non-existent outcome returns 404 ─────────────────
        UUID nonExistentId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        mockMvc.perform(
                        get("/api/v1/outcomes/{id}/metadata", nonExistentId)
                                .header("Authorization", TOKEN_ADMIN))
                .andExpect(status().isNotFound());

        // ── Scenario 6: PATCH progress — updates currentValue, recomputes pct ─
        // Before: currentValue=80, targetValue=200 → METRIC score = 0.40
        // After patch: currentValue=120, targetValue=200 → METRIC score = 0.60
        // composite = 0.6*0.60 + 0.4*0.0 (no locked plans) = 0.36 → progressPct ≈ 36.00
        MvcResult patchResult = mockMvc.perform(
                        patch("/api/v1/outcomes/{id}/progress", OUTCOME_ID)
                                .header("Authorization", TOKEN_ADMIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentValue\": 120.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPct").isNumber())
                .andReturn();

        JsonNode patched = objectMapper.readTree(
                patchResult.getResponse().getContentAsString());
        assertThat(new BigDecimal(patched.get("progressPct").asText()))
                .isGreaterThan(BigDecimal.ZERO);
        // Verify currentValue was actually updated to the patched value.
        assertThat(new BigDecimal(patched.get("currentValue").asText()))
                .isEqualByComparingTo(new BigDecimal("120.0"));

        // ── Scenario 8: GET urgency summary returns outcomes array ────────────
        mockMvc.perform(
                        get("/api/v1/outcomes/urgency-summary")
                                .header("Authorization", TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes").isArray())
                .andExpect(
                        jsonPath("$.outcomes[?(@.outcomeId == '" + OUTCOME_ID + "')]")
                                .exists());

        // ── Scenario 9: GET strategic slack returns slack info ────────────────
        mockMvc.perform(
                        get("/api/v1/team/strategic-slack")
                                .header("Authorization", TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slack").exists())
                .andExpect(jsonPath("$.slack.slackBand").isNotEmpty())
                .andExpect(jsonPath("$.slack.strategicFocusFloor").isNumber())
                .andExpect(jsonPath("$.slack.atRiskCount").isNumber())
                .andExpect(jsonPath("$.slack.criticalCount").isNumber());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a JSON request body for a METRIC-type outcome metadata upsert.
     *
     * @param metricName   name of the tracked metric
     * @param targetValue  numeric goal
     * @param currentValue current metric value
     * @param unit         unit of measurement
     * @param targetDate   ISO-8601 target date string (e.g. {@code "2099-12-31"})
     * @return JSON string suitable for use as a MockMvc request body
     */
    private String metricRequestBody(
            String metricName,
            double targetValue,
            double currentValue,
            String unit,
            String targetDate
    ) {
        return String.format(
                "{\"targetDate\":\"%s\",\"progressType\":\"METRIC\","
                        + "\"metricName\":\"%s\",\"targetValue\":%s,"
                        + "\"currentValue\":%s,\"unit\":\"%s\"}",
                targetDate, metricName, targetValue, currentValue, unit
        );
    }
}
