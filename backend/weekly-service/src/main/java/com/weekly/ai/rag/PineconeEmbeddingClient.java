package com.weekly.ai.rag;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openapitools.db_control.client.model.DeletionProtection;
import org.openapitools.db_control.client.model.IndexList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production {@link EmbeddingClient} backed by Pinecone (vector store) and
 * OpenAI {@code text-embedding-3-small} (embedding generation).
 *
 * <p>On construction the client checks whether the configured Pinecone index
 * already exists and creates a serverless index (cosine, 1536 dims, AWS us-east-1)
 * if it does not.  Any failure during index validation is logged as a warning
 * rather than crashing startup — the existing index host can still be used for
 * data-plane operations.
 *
 * <p>Configuration properties (set via environment variables in deployed envs):
 * <ul>
 *   <li>{@code weekly.rag.pinecone.api-key} / {@code PINECONE_API_KEY}</li>
 *   <li>{@code weekly.rag.pinecone.index-name} (defaults to {@code spice-fortran})</li>
 *   <li>{@code weekly.rag.openai.api-key} / {@code OPENAI_API_KEY}</li>
 * </ul>
 */
public class PineconeEmbeddingClient implements EmbeddingClient {

    private static final Logger LOG = LoggerFactory.getLogger(PineconeEmbeddingClient.class);

    /** Dimension for {@code text-embedding-3-small}. */
    static final int DIMENSIONS = 1536;

    /** Model used for all embedding requests. */
    static final String EMBEDDING_MODEL = "text-embedding-3-small";

    private final Index index;
    private final OpenAiService openAiService;

    /**
     * Constructs the client and connects to the specified Pinecone index.
     *
     * @param pineconeApiKey Pinecone API key
     * @param indexName      name of the Pinecone index (must already exist or will be created)
     * @param openAiApiKey   OpenAI API key for embedding generation
     */
    public PineconeEmbeddingClient(String pineconeApiKey, String indexName, String openAiApiKey) {
        Pinecone pinecone = new Pinecone.Builder(pineconeApiKey).build();
        this.openAiService = new OpenAiService(openAiApiKey);
        this.index = initIndex(pinecone, indexName);
    }

    // ── EmbeddingClient ───────────────────────────────────────────────────────

    @Override
    public float[] embed(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(EMBEDDING_MODEL)
                .input(List.of(text))
                .build();
        List<Double> values = openAiService.createEmbeddings(request)
                .getData().get(0).getEmbedding();
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    @Override
    public void upsert(String id, float[] vector, Map<String, Object> metadata) {
        List<Float> values = toFloatList(vector);
        Struct metaStruct = toStruct(metadata != null ? metadata : Map.of());
        index.upsert(id, values, null, null, metaStruct, null);
    }

    @Override
    public List<ScoredMatch> query(float[] vector, int topK, Map<String, Object> filter) {
        List<Float> values = toFloatList(vector);
        Struct filterStruct = (filter != null && !filter.isEmpty()) ? toStruct(filter) : null;
        QueryResponseWithUnsignedIndices response = index.queryByVector(
                topK, values, null, filterStruct, false, true);
        List<ScoredMatch> matches = new ArrayList<>();
        for (ScoredVectorWithUnsignedIndices sv : response.getMatchesList()) {
            matches.add(new ScoredMatch(sv.getId(), sv.getScore(), fromStruct(sv.getMetadata())));
        }
        return matches;
    }

    @Override
    public void delete(String id) {
        index.deleteByIds(List.of(id), null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Index initIndex(Pinecone pinecone, String indexName) {
        try {
            IndexList indexList = pinecone.listIndexes();
            boolean exists = indexList.getIndexes() != null
                    && indexList.getIndexes().stream()
                            .anyMatch(m -> indexName.equals(m.getName()));
            if (!exists) {
                LOG.info("Pinecone index '{}' not found — creating serverless index "
                        + "(cosine, {} dims, aws/us-east-1)", indexName, DIMENSIONS);
                pinecone.createServerlessIndex(
                        indexName, "cosine", DIMENSIONS, "aws", "us-east-1",
                        DeletionProtection.DISABLED);
                LOG.info("Pinecone index '{}' created successfully", indexName);
            } else {
                LOG.info("Pinecone index '{}' already exists — connecting", indexName);
            }
        } catch (Exception e) {
            LOG.warn("Could not verify Pinecone index '{}' ({}); "
                    + "proceeding with data-plane connection anyway", indexName, e.getMessage());
        }
        return pinecone.getIndexConnection(indexName);
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    /** Converts a Java {@code Map<String, Object>} to a protobuf {@link Struct}. */
    private static Struct toStruct(Map<String, Object> map) {
        Struct.Builder builder = Struct.newBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            builder.putFields(entry.getKey(), toValue(entry.getValue()));
        }
        return builder.build();
    }

    private static Value toValue(Object val) {
        Value.Builder vb = Value.newBuilder();
        if (val instanceof String) {
            vb.setStringValue((String) val);
        } else if (val instanceof Boolean) {
            vb.setBoolValue((Boolean) val);
        } else if (val instanceof Number) {
            vb.setNumberValue(((Number) val).doubleValue());
        } else if (val == null) {
            vb.setNullValueValue(0);
        } else {
            vb.setStringValue(val.toString());
        }
        return vb.build();
    }

    /** Converts a protobuf {@link Struct} to a Java {@code Map<String, Object>}. */
    private static Map<String, Object> fromStruct(Struct struct) {
        if (struct == null) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            Value v = entry.getValue();
            Object javaVal = switch (v.getKindCase()) {
                case STRING_VALUE -> v.getStringValue();
                case BOOL_VALUE -> v.getBoolValue();
                case NUMBER_VALUE -> v.getNumberValue();
                default -> null;
            };
            map.put(entry.getKey(), javaVal);
        }
        return map;
    }
}
