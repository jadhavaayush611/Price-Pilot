package com.pricepilot.ai;

import java.util.List;
import java.util.UUID;

/**
 * Interface representing the REST/gRPC client to communicate with downstream Python/FastAPI microservices.
 * Used for running machine learning tasks like embedding generation or neural network recommendations.
 */
public interface PythonAiServiceClient {

    /**
     * Calls python microservice to generate query embeddings.
     *
     * @param text The input text.
     * @return Vector embeddings array.
     */
    float[] fetchEmbeddings(String text);

    /**
     * Calls python microservice to fetch collaborative filtering product IDs.
     *
     * @param userId User UUID.
     * @return List of recommended product UUIDs.
     */
    List<UUID> fetchRecommendedProductIds(UUID userId);
}
