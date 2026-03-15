package com.weekly.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Spring Security filter chain.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Protected endpoints return 401 when credentials are absent</li>
 *   <li>Protected endpoints return 401 when required legacy auth headers are absent</li>
 *   <li>Protected endpoints are reachable with either a structured dev bearer token
 *       or legacy dev headers</li>
 *   <li>The health endpoint is publicly accessible (no auth required)</li>
 * </ul>
 *
 * <p>Uses the {@code test} profile so {@link DevRequestAuthenticator} is active.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // ── Health endpoint (permit-all) ─────────────────────────────────────────

    @Test
    void healthEndpointRequiresNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthEndpointRequiresNoAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ── Missing credentials → 401 ────────────────────────────────────────────

    @Test
    void planEndpointReturns401WhenNoHeadersProvided() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/plans/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void commitEndpointReturns401WhenNoHeadersProvided() throws Exception {
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/commits"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void teamSummaryEndpointReturns401WhenNoHeadersProvided() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/team/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void planEndpointReturns401WhenOrgIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/plans/me")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ── Valid credentials → endpoint reached (functional response) ───────────

    @Test
    void planEndpointReachableWithStructuredDevBearerToken() throws Exception {
        // 404 is a functional response — it means auth passed and controller ran
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/plans/me")
                        .header("Authorization", "Bearer dev:" + USER_ID + ":" + ORG_ID + ":IC"))
                .andExpect(status().isNotFound());
    }

    @Test
    void planEndpointReachableWithValidOrgIdHeader() throws Exception {
        // 404 is a functional response — it means auth passed and controller ran
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/plans/me")
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void commitListReachableWithValidOrgIdHeader() throws Exception {
        // 404 means auth passed and controller ran (plan doesn't exist)
        mockMvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/commits")
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPlanReachableWithValidHeaders() throws Exception {
        // Plan creation on a non-Monday week → 422 (functional response, auth passed)
        String tuesday = "2026-03-10";
        mockMvc.perform(post("/api/v1/weeks/" + tuesday + "/plans")
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── Invalid UUID header → 401 ────────────────────────────────────────────

    @Test
    void planEndpointReturns401WhenOrgIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/plans/me")
                        .header("X-Org-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void planEndpointReturns401WhenUserIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/plans/me")
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ── X-Roles header is parsed correctly ──────────────────────────────────

    @Test
    void managerRoleIsPassedViaHeader() throws Exception {
        // Team summary endpoint is reachable with MANAGER role header
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/team/summary")
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Roles", "MANAGER"))
                .andExpect(status().isOk());
    }

    @Test
    void managerRoleIsPassedViaStructuredDevBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/2026-03-09/team/summary")
                        .header("Authorization", "Bearer dev:" + USER_ID + ":" + ORG_ID + ":MANAGER"))
                .andExpect(status().isOk());
    }
}
