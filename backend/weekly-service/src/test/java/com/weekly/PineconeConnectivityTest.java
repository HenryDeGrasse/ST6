package com.weekly;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinecone.clients.Pinecone;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openapitools.db_control.client.model.IndexList;

/**
 * Manual connectivity test for the Pinecone API key supplied by the user.
 *
 * <p><b>This test is {@link Disabled} by default</b> and should NOT be enabled in CI.
 * It exists only to validate the Pinecone key and index before configuring the
 * production RAG pipeline.
 *
 * <h3>How to enable</h3>
 * <ol>
 *   <li>Remove the {@code @Disabled} annotation.</li>
 *   <li>Run: {@code ./gradlew test --tests com.weekly.PineconeConnectivityTest}.</li>
 * </ol>
 *
 * <h3>Expected outcome</h3>
 * <ul>
 *   <li>If the key is valid: the test lists all indexes and asserts that the call
 *       succeeds.  If no indexes exist yet that is fine — the test only checks
 *       connectivity, not the presence of a specific index.</li>
 *   <li>If the key is invalid / expired: the test fails with an
 *       {@code UnauthorizedException}.  In that case the user should generate a
 *       new Pinecone API key and create a new serverless index
 *       (cosine metric, 1536 dimensions, AWS us-east-1), then update
 *       {@code PINECONE_API_KEY} and {@code weekly.rag.pinecone.index-name}
 *       in the deployment config.</li>
 * </ul>
 *
 * <h3>API key</h3>
 * <p>Set via {@code PINECONE_API_KEY} environment variable. Never hardcode.</p>
 *
 * <h3>Pinecone index used by this project</h3>
 * <p>The production index name is configured via {@code weekly.rag.pinecone.index-name}
 * (default: {@code spice-fortran}). If this index does not exist on the account,
 * {@link com.weekly.ai.rag.PineconeEmbeddingClient} will create it automatically
 * at startup (serverless, cosine, 1536 dims, AWS us-east-1).
 */
@Disabled(
        "Manual connectivity test — remove @Disabled to run against live Pinecone. "
        + "Do NOT enable in CI.")
class PineconeConnectivityTest {

    /**
     * API key provided by the user.
     *
     * <p>If this key is no longer valid (expired or revoked), set
     * {@code PINECONE_API_KEY} as an environment variable and update this constant,
     * or ask the user to create a new key and index on
     * <a href="https://app.pinecone.io">app.pinecone.io</a>.
     */
    private static final String PINECONE_API_KEY =
            System.getenv().getOrDefault(
                    "PINECONE_API_KEY",
                    "" // Must be set via environment variable — never hardcode API keys
            );

    /** Default Pinecone index name used by the RAG pipeline. */
    private static final String INDEX_NAME = System.getenv().getOrDefault(
            "PINECONE_INDEX_NAME", "spice-fortran");

    /**
     * Verifies that the Pinecone API key can list indexes (control-plane connectivity).
     *
     * <p>A successful run means:
     * <ul>
     *   <li>The key is valid and has not been revoked.</li>
     *   <li>The Pinecone control-plane API is reachable from this machine.</li>
     * </ul>
     *
     * <p>If the call throws an unauthorized exception, a new key is needed.
     * If it throws a network exception, check firewall/proxy settings.
     */
    @Test
    void pineconeApiKeyCanListIndexes() {
        Pinecone pinecone = new Pinecone.Builder(PINECONE_API_KEY).build();

        IndexList indexList = pinecone.listIndexes();

        assertThat(indexList).isNotNull();
        // listIndexes() may return an empty list if no indexes have been created yet —
        // that is fine; we only check that the call succeeded without throwing.
        System.out.printf(
                "[PineconeConnectivityTest] listIndexes() succeeded. "
                + "Found %d index(es): %s%n",
                indexList.getIndexes() == null ? 0 : indexList.getIndexes().size(),
                indexList.getIndexes() == null ? "[]"
                        : indexList.getIndexes().stream()
                                .map(m -> m.getName())
                                .toList()
        );
    }

    /**
     * Verifies whether the target index ({@value #INDEX_NAME}) exists in the account.
     *
     * <p>If the index does not exist, {@link com.weekly.ai.rag.PineconeEmbeddingClient}
     * will create it automatically at startup. However, first-time creation can take
     * 30–60 seconds, which may cause a startup delay in production. Pre-creating the
     * index via the Pinecone console or the management SDK avoids this delay.
     */
    @Test
    void targetIndexExistsOrWillBeCreatedAutomatically() {
        Pinecone pinecone = new Pinecone.Builder(PINECONE_API_KEY).build();

        IndexList indexList = pinecone.listIndexes();
        assertThat(indexList).isNotNull();

        boolean exists = indexList.getIndexes() != null
                && indexList.getIndexes().stream()
                        .anyMatch(m -> INDEX_NAME.equals(m.getName()));

        if (exists) {
            System.out.printf(
                    "[PineconeConnectivityTest] Index '%s' exists — ready to use.%n", INDEX_NAME);
        } else {
            System.out.printf(
                    "[PineconeConnectivityTest] Index '%s' does NOT exist yet. "
                    + "PineconeEmbeddingClient will create it automatically at startup.%n",
                    INDEX_NAME);
        }
        // Both outcomes are acceptable — the test just documents the state.
        // No assertion failure; the output explains what was found.
    }
}
