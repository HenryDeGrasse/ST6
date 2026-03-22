package com.weekly.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that creates the appropriate {@link EmbeddingClient} bean.
 *
 * <p>Provider selection via {@code weekly.rag.provider}:
 * <ul>
 *   <li>{@code memory} (default) — in-process hash-based stub; no external calls;
 *       used for {@code local} and {@code test} profiles.</li>
 *   <li>{@code pinecone} — production Pinecone + OpenAI backend; requires
 *       {@code PINECONE_API_KEY} and {@code OPENAI_API_KEY} environment variables;
 *       used for {@code dev}, {@code staging}, and {@code prod} profiles.</li>
 * </ul>
 *
 * <p>If {@code pinecone} is selected but either API key is blank, the bean
 * gracefully falls back to the in-memory implementation and logs an error.
 */
@Configuration
public class RagConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RagConfiguration.class);

    @Bean
    public EmbeddingClient embeddingClient(
            @Value("${weekly.rag.provider:memory}") String provider,
            @Value("${weekly.rag.pinecone.api-key:}") String pineconeApiKey,
            @Value("${weekly.rag.pinecone.index-name:spice-fortran}") String indexName,
            @Value("${weekly.rag.openai.api-key:}") String openAiApiKey
    ) {
        return switch (provider.toLowerCase()) {
            case "pinecone" -> {
                if (pineconeApiKey.isBlank()) {
                    LOG.error("weekly.rag.provider=pinecone but PINECONE_API_KEY is not set; "
                            + "falling back to in-memory EmbeddingClient");
                    yield new InMemoryEmbeddingClient();
                }
                if (openAiApiKey.isBlank()) {
                    LOG.error("weekly.rag.provider=pinecone but OPENAI_API_KEY is not set; "
                            + "falling back to in-memory EmbeddingClient");
                    yield new InMemoryEmbeddingClient();
                }
                LOG.info("RAG provider: Pinecone (index={})", indexName);
                yield new PineconeEmbeddingClient(pineconeApiKey, indexName, openAiApiKey);
            }
            case "memory" -> {
                LOG.info("RAG provider: in-memory (local / test mode)");
                yield new InMemoryEmbeddingClient();
            }
            default -> {
                LOG.warn("Unknown weekly.rag.provider='{}'; falling back to in-memory", provider);
                yield new InMemoryEmbeddingClient();
            }
        };
    }
}
