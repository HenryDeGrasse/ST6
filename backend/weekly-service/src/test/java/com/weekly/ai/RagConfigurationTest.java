package com.weekly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekly.ai.rag.InMemoryEmbeddingClient;
import com.weekly.ai.rag.RagConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RagConfiguration}.
 *
 * <p>Verifies that the correct {@link com.weekly.ai.rag.EmbeddingClient} implementation
 * is returned for each provider selection, without spinning up a Spring context.
 * Pinecone connectivity is NOT tested here — that requires live credentials.
 */
class RagConfigurationTest {

    private final RagConfiguration config = new RagConfiguration();

    @Test
    void memoryProviderReturnsInMemoryEmbeddingClient() {
        assertThat(config.embeddingClient("memory", "", "spice-fortran", ""))
                .isInstanceOf(InMemoryEmbeddingClient.class);
    }

    @Test
    void unknownProviderFallsBackToInMemory() {
        assertThat(config.embeddingClient("unknown-provider", "", "spice-fortran", ""))
                .isInstanceOf(InMemoryEmbeddingClient.class);
    }

    @Test
    void pineconeProviderWithBlankPineconeKeyFallsBackToInMemory() {
        // No PINECONE_API_KEY set → should fall back gracefully
        assertThat(config.embeddingClient("pinecone", "", "spice-fortran", "some-openai-key"))
                .isInstanceOf(InMemoryEmbeddingClient.class);
    }

    @Test
    void pineconeProviderWithBlankOpenAiKeyFallsBackToInMemory() {
        // PINECONE_API_KEY set but no OPENAI_API_KEY → should fall back gracefully
        assertThat(config.embeddingClient("pinecone", "some-pinecone-key", "spice-fortran", ""))
                .isInstanceOf(InMemoryEmbeddingClient.class);
    }

    @Test
    void memoryProviderIsCaseInsensitive() {
        assertThat(config.embeddingClient("MEMORY", "", "spice-fortran", ""))
                .isInstanceOf(InMemoryEmbeddingClient.class);
        assertThat(config.embeddingClient("Memory", "", "spice-fortran", ""))
                .isInstanceOf(InMemoryEmbeddingClient.class);
    }
}
