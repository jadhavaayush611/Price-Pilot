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
}
