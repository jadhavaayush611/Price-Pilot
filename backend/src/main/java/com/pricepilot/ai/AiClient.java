package com.pricepilot.ai;

import com.pricepilot.ai.dto.AiPredictRequest;
import com.pricepilot.ai.dto.AiPredictResponse;

public interface AiClient {
    
    /**
     * Sends recommendation requests to FastAPI AI Microservice.
     *
     * @param request The typed request payload.
     * @return The response with recommended item IDs, scores, and reasons.
     */
    AiPredictResponse predict(AiPredictRequest request);

    /**
     * Checks if the FastAPI AI microservice is healthy and reachable.
     */
    boolean isAvailable();

    /**
     * Forwards chat message requests to the FastAPI AI microservice assistant endpoint.
     */
    java.util.Map<String, Object> chat(java.util.Map<String, Object> request, String authorizationHeader);

    /**
     * Forwards product comparison requests to the FastAPI assistant compare endpoint.
     */
    java.util.Map<String, Object> compare(java.util.Map<String, Object> request, String authorizationHeader);

    /**
     * Forwards single-turn question requests to the FastAPI assistant ask endpoint.
     */
    java.util.Map<String, Object> ask(java.util.Map<String, Object> request, String authorizationHeader);

    /**
     * Forwards memory clearing requests to the FastAPI assistant clear memory endpoint.
     */
    java.util.Map<String, Object> clearMemory(java.util.Map<String, Object> request, String authorizationHeader);
}

