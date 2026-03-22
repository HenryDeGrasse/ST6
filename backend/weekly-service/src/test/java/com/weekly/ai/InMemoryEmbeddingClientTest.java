package com.weekly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.weekly.ai.rag.InMemoryEmbeddingClient;
import com.weekly.ai.rag.ScoredMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryEmbeddingClient}.
 *
 * <p>Verifies: upsert/query/delete lifecycle, cosine similarity, filter support,
 * and the deterministic hash-based stub embedder.
 */
class InMemoryEmbeddingClientTest {

    private InMemoryEmbeddingClient client;

    @BeforeEach
    void setUp() {
        client = new InMemoryEmbeddingClient();
    }

    // ── upsert / size ─────────────────────────────────────────────────────────

    @Test
    void upsertIncreasesSize() {
        assertThat(client.size()).isEqualTo(0);
        float[] v = client.embed("hello");
        client.upsert("id1", v, Map.of("k", "v"));
        assertThat(client.size()).isEqualTo(1);
    }

    @Test
    void upsertOverwritesExistingId() {
        float[] v1 = client.embed("first");
        float[] v2 = client.embed("second");
        client.upsert("id1", v1, Map.of("version", "1"));
        client.upsert("id1", v2, Map.of("version", "2"));
        assertThat(client.size()).isEqualTo(1);

        List<ScoredMatch> results = client.query(v2, 1, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata().get("version")).isEqualTo("2");
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Test
    void queryReturnsTopK() {
        for (int i = 0; i < 10; i++) {
            client.upsert("id" + i, client.embed("text " + i), Map.of());
        }
        List<ScoredMatch> results = client.query(client.embed("text 5"), 3, null);
        assertThat(results).hasSize(3);
    }

    @Test
    void queryOrderedByDescendingScore() {
        client.upsert("a", client.embed("alpha"), Map.of());
        client.upsert("b", client.embed("beta"), Map.of());
        client.upsert("c", client.embed("gamma"), Map.of());

        float[] query = client.embed("alpha");
        List<ScoredMatch> results = client.query(query, 3, null);

        // The most similar entry should be "alpha" itself (score ≈ 1.0)
        assertThat(results.get(0).id()).isEqualTo("a");
        // Scores should be descending
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
        }
    }

    @Test
    void queryExactMatchSelfReturnsScoreNearOne() {
        String text = "the quick brown fox";
        float[] vector = client.embed(text);
        client.upsert("fox", vector, Map.of());

        List<ScoredMatch> results = client.query(vector, 1, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("fox");
        assertThat(results.get(0).score()).isCloseTo(1.0f, within(1e-4f));
    }

    @Test
    void queryWithFilterOnlyReturnsMatchingEntries() {
        client.upsert("issue1", client.embed("auth bug"), Map.of("teamId", "team-A"));
        client.upsert("issue2", client.embed("auth bug"), Map.of("teamId", "team-B"));
        client.upsert("issue3", client.embed("deploy task"), Map.of("teamId", "team-A"));

        List<ScoredMatch> results = client.query(
                client.embed("auth bug"), 10, Map.of("teamId", "team-A"));

        assertThat(results).extracting(ScoredMatch::id)
                .containsExactlyInAnyOrder("issue1", "issue3")
                .doesNotContain("issue2");
    }

    @Test
    void queryEmptyStoreReturnsEmpty() {
        List<ScoredMatch> results = client.query(client.embed("anything"), 5, null);
        assertThat(results).isEmpty();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void deleteRemovesEntry() {
        float[] v = client.embed("hello");
        client.upsert("id1", v, Map.of());
        assertThat(client.size()).isEqualTo(1);

        client.delete("id1");
        assertThat(client.size()).isEqualTo(0);
    }

    @Test
    void deleteNonExistentIdIsNoOp() {
        // Should not throw
        client.delete("does-not-exist");
        assertThat(client.size()).isEqualTo(0);
    }

    @Test
    void deleteRemovedEntryNotReturnedByQuery() {
        client.upsert("a", client.embed("apple"), Map.of());
        client.upsert("b", client.embed("apple"), Map.of());
        client.delete("a");

        List<ScoredMatch> results = client.query(client.embed("apple"), 10, null);
        assertThat(results).extracting(ScoredMatch::id).containsOnly("b");
    }

    // ── cosine similarity ─────────────────────────────────────────────────────

    @Test
    void cosineSimilarityIdenticalVectorsReturnsOne() {
        float[] v = InMemoryEmbeddingClient.hashEmbed("hello", 64);
        float sim = InMemoryEmbeddingClient.cosineSimilarity(v, v);
        assertThat(sim).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void cosineSimilarityZeroVectorsReturnsZero() {
        float[] a = new float[64]; // all zeros
        float[] b = new float[64];
        assertThat(InMemoryEmbeddingClient.cosineSimilarity(a, b)).isEqualTo(0.0f);
    }

    @Test
    void cosineSimilarityDifferentLengthsThrows() {
        float[] a = new float[4];
        float[] b = new float[8];
        assertThatThrownBy(() -> InMemoryEmbeddingClient.cosineSimilarity(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── hash embedder ─────────────────────────────────────────────────────────

    @Test
    void hashEmbedIsDeterministic() {
        float[] v1 = InMemoryEmbeddingClient.hashEmbed("same text", 128);
        float[] v2 = InMemoryEmbeddingClient.hashEmbed("same text", 128);
        assertThat(v1).containsExactly(v2);
    }

    @Test
    void hashEmbedDifferentInputsDifferentVectors() {
        float[] v1 = InMemoryEmbeddingClient.hashEmbed("text A", 128);
        float[] v2 = InMemoryEmbeddingClient.hashEmbed("text B", 128);
        assertThat(v1).isNotEqualTo(v2);
    }

    @Test
    void hashEmbedProducesUnitVector() {
        float[] v = InMemoryEmbeddingClient.hashEmbed("normalisation test", 256);
        double norm = 0.0;
        for (float val : v) {
            norm += (double) val * val;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void hashEmbedRespectsDimensions() {
        assertThat(InMemoryEmbeddingClient.hashEmbed("x", 64)).hasSize(64);
        assertThat(InMemoryEmbeddingClient.hashEmbed("x", 1536)).hasSize(1536);
    }
}
