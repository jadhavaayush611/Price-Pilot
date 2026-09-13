package com.pricepilot.intelligence.semantic.service;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchRequest;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production implementation of VectorSearchService.
 */
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchServiceImpl.class);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SemanticIntelligenceProperties properties;

    public VectorSearchServiceImpl(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            SemanticIntelligenceProperties properties) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "EmbeddingService cannot be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null");
        this.properties = Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");
    }

    @Override
    public List<SimilaritySearchResult> searchByVector(SimilaritySearchRequest request) {
        Objects.requireNonNull(request, "SimilaritySearchRequest cannot be null");
        return vectorStore.similaritySearch(request);
    }

    @Override
    public List<SimilaritySearchResult> searchByText(String queryText, String entityType, int topK, double minScore) {
        return searchByTextWithFilters(queryText, entityType, topK, minScore, Collections.emptyMap());
    }

    @Override
    public List<SimilaritySearchResult> searchByTextWithFilters(
            String queryText,
            String entityType,
            int topK,
            double minScore,
            Map<String, String> filterAttributes) {
        if (!properties.isEnabled()) {
            log.warn("Semantic vector search requested while semantic intelligence is disabled");
            return Collections.emptyList();
        }

        EmbeddingVector queryVector = embeddingService.generateEmbedding(queryText);

        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(queryVector)
                .entityType(entityType)
                .modelName(embeddingService.getModelName())
                .modelVersion(embeddingService.getModelVersion())
                .topK(topK)
                .minScore(minScore)
                .filterAttributes(filterAttributes)
                .build();

        return vectorStore.similaritySearch(request);
    }
}
