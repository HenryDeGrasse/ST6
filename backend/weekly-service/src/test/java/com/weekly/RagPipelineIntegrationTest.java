package com.weekly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.rag.EmbeddingClient;
import com.weekly.ai.rag.IssueEmbeddingService;
import com.weekly.ai.rag.ScoredMatch;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Integration test for the Phase 6 RAG (Retrieval-Augmented Generation) pipeline.
 *
 * <p>Uses the in-memory {@link com.weekly.ai.rag.InMemoryEmbeddingClient} (the default
 * for the {@code test} profile) so no external Pinecone or OpenAI calls are needed.
 *
 * <p>Covers:
 * <ol>
 *   <li>Create issues via the REST API.</li>
 *   <li>Trigger embedding via {@link IssueEmbeddingService#embedIssue(UUID)}.</li>
 *   <li>Verify vectors are stored in the in-memory client.</li>
 *   <li>Semantic search returns the relevant issue via {@code POST /ai/search-issues}.</li>
 *   <li>HyDE recommendation endpoint {@code POST /ai/recommend-weekly-issues} returns
 *       a non-empty result list.</li>
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
        "tenant.rls.enabled=false",
        "weekly.rag.provider=memory"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RagPipelineIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("weekly")
                    .withUsername("weekly")
                    .withPassword("weekly")
                    .withInitScript("init-rls-user.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The EmbeddingClient bean. The test profile uses {@code weekly.rag.provider=memory},
     * so this will be an {@link com.weekly.ai.rag.InMemoryEmbeddingClient} at runtime.
     * We cast to the concrete type locally where we need to call {@code size()}.
     */
    @Autowired
    private EmbeddingClient embeddingClient;

    /** Embedding service — injected so we can trigger embeddings synchronously. */
    @Autowired
    private IssueEmbeddingService issueEmbeddingService;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000088");
    private static final UUID USER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000088");
    private static final String TOKEN =
            "Bearer dev:" + USER_ID + ":" + ORG_ID + ":ADMIN,MANAGER,IC";

    /**
     * End-to-end RAG pipeline: create issues → embed → query via embedding client
     * → semantic search endpoint → HyDE recommendation endpoint.
     */
    @Test
    void ragPipelineCreateEmbedSearchRecommend() throws Exception {
        // ── 1. Create a team ──────────────────────────────────
        String createTeamBody = objectMapper.writeValueAsString(
                Map.of("name", "RAG Test Team", "keyPrefix", "RAG")
        );
        MvcResult teamResult = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTeamBody))
                .andExpect(status().isCreated())
                .andReturn();

        String teamId = objectMapper.readTree(
                teamResult.getResponse().getContentAsString()).get("id").asText();

        // ── 2. Create three issues with different semantic domains ─────────
        String authIssueId = createIssue(teamId,
                "Implement JWT refresh token rotation",
                "Implement sliding-window refresh token rotation using the auth service. " +
                "Mitigate token theft by issuing a new refresh token on every access.");

        String perfIssueId = createIssue(teamId,
                "Optimize slow database queries for the reporting module",
                "Profile and fix N+1 query patterns in the reporting service. " +
                "Add indexes to speed up the weekly aggregation pipeline.");

        String uiIssueId = createIssue(teamId,
                "Build dark mode toggle for the settings page",
                "Implement a user-controlled dark mode switch that persists to the profile. " +
                "Follow the Tailwind CSS theming guidelines.");

        // ── 3. Trigger embeddings synchronously ───────────────────────────
        issueEmbeddingService.embedIssue(UUID.fromString(authIssueId));
        issueEmbeddingService.embedIssue(UUID.fromString(perfIssueId));
        issueEmbeddingService.embedIssue(UUID.fromString(uiIssueId));

        // ── 4. Verify vectors are in the store ────────────────────────────
        // The in-memory client should now hold at least 3 vectors (may have been
        // pre-populated by other tests sharing the context, so use >=).
        int storeSize = vectorStoreSize();
        assertThat(storeSize).isGreaterThanOrEqualTo(3);

        // ── 5. Direct vector query returns results for an auth-related query ────
        // Note: The InMemoryEmbeddingClient uses a hash-based stub embedder that does not
        // capture semantic similarity. We only assert that the query returns results and that
        // the org-scoped filter works (all returned issues are from the test org).
        float[] authQueryVector = embeddingClient.embed("JWT token rotation refresh security");
        List<ScoredMatch> directResults = embeddingClient.query(
                authQueryVector, 3, Map.of("orgId", ORG_ID.toString()));

        assertThat(directResults).isNotEmpty();
        // All results must belong to the test org (filter is applied)
        directResults.forEach(m ->
                assertThat(m.metadata().get("orgId"))
                        .as("Result should belong to test org")
                        .isEqualTo(ORG_ID.toString())
        );
        // All three embedded issues should be retrievable (query limit = 3)
        assertThat(directResults).extracting(ScoredMatch::id)
                .containsExactlyInAnyOrder(authIssueId, perfIssueId, uiIssueId);

        // ── 6. Search endpoint: semantic search returns relevant result ────
        String searchBody = objectMapper.writeValueAsString(Map.of(
                "query", "JWT access token refresh rotation",
                "limit", 5
        ));

        MvcResult searchResult = mockMvc.perform(post("/api/v1/ai/search-issues")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.hits").isArray())
                .andReturn();

        JsonNode searchJson = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        JsonNode hits = searchJson.get("hits");
        assertThat(hits.size()).isGreaterThan(0);

        // ── 7. HyDE recommendation endpoint returns results ────────────────
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        String recommendBody = objectMapper.writeValueAsString(Map.of(
                "weekStart", weekStart,
                "maxItems", 3
        ));

        mockMvc.perform(post("/api/v1/ai/recommend-weekly-issues")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recommendBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.recommendations").isArray());
    }

    /**
     * Verifies that deleting an embedding removes the vector from the store.
     */
    @Test
    void deleteEmbeddingRemovesVectorFromStore() throws Exception {
        // Create a team and issue
        String createTeamBody = objectMapper.writeValueAsString(
                Map.of("name", "Delete Test Team", "keyPrefix", "DEL")
        );
        MvcResult teamResult = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTeamBody))
                .andExpect(status().isCreated())
                .andReturn();

        String teamId = objectMapper.readTree(
                teamResult.getResponse().getContentAsString()).get("id").asText();

        String issueId = createIssue(teamId,
                "Issue to be deleted from vector store",
                "This issue will be embedded and then deleted from the vector store.");

        UUID issueUuid = UUID.fromString(issueId);

        // Embed the issue
        issueEmbeddingService.embedIssue(issueUuid);
        int sizeAfterEmbed = vectorStoreSize();
        assertThat(sizeAfterEmbed).isGreaterThanOrEqualTo(1);

        // Delete the embedding
        issueEmbeddingService.deleteEmbedding(issueUuid);

        // Verify it's gone
        assertThat(vectorStoreSize()).isLessThan(sizeAfterEmbed);

        // Direct query should no longer return this issue
        float[] queryVector = embeddingClient.embed("deleted from vector store");
        List<ScoredMatch> results = embeddingClient.query(queryVector, 10, null);
        boolean found = results.stream().anyMatch(m -> issueId.equals(m.id()));
        assertThat(found).as("Deleted issue should not appear in query results").isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the number of vectors currently in the embedding store.
     * Casts the bean to {@link com.weekly.ai.rag.InMemoryEmbeddingClient} since
     * {@code weekly.rag.provider=memory} is set for the test profile.
     */
    private int vectorStoreSize() {
        return ((com.weekly.ai.rag.InMemoryEmbeddingClient) embeddingClient).size();
    }

    /** Creates an issue via the REST API and returns the issue ID. */
    private String createIssue(String teamId, String title, String description) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "description", description,
                "effortType", "BUILD",
                "chessPriority", "ROOK"
        ));
        MvcResult result = mockMvc.perform(
                        post("/api/v1/teams/{teamId}/issues", teamId)
                                .header("Authorization", TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}
