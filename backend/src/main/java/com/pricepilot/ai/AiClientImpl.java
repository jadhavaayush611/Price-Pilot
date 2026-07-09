package com.pricepilot.ai;

import com.pricepilot.ai.dto.AiPredictRequest;
import com.pricepilot.ai.dto.AiPredictResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AiClientImpl implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClientImpl.class);

    @Value("${pricepilot.ai.url:http://localhost:8000}")
    private String aiUrl;

    @Value("${pricepilot.ai.timeout:5000}")
    private int timeout;

    @Value("${pricepilot.ai.retry-count:3}")
    private int retryCount;

    @Value("${pricepilot.ai.api-key:pricepilot-secret-api-key}")
    private String apiKey;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        
        this.restTemplate = new RestTemplate(requestFactory);
        
        // Add header interceptor for API security key
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-API-Key", apiKey);
            return execution.execute(request, body);
        });
    }

    @Override
    public AiPredictResponse predict(AiPredictRequest request) {
        String endpoint = aiUrl + "/recommendations/predict";
        
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                log.debug("Sending prediction request to FastAPI, attempt {}/{}", attempt, retryCount);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<AiPredictRequest> httpEntity = new HttpEntity<>(request, headers);
                return restTemplate.postForObject(endpoint, httpEntity, AiPredictResponse.class);
            } catch (RestClientException e) {
                lastException = e;
                log.warn("Attempt {} to predict failed: {}", attempt, e.getMessage());
            }
        }
        
        throw new RuntimeException("FastAPI prediction failed after " + retryCount + " attempts", lastException);
    }

    @Override
    public boolean isAvailable() {
        String endpoint = aiUrl + "/health";
        try {
            log.debug("Checking health of FastAPI AI service at {}", endpoint);
            @SuppressWarnings("rawtypes")
            Map response = restTemplate.getForObject(endpoint, Map.class);
            if (response != null && "UP".equalsIgnoreCase((String) response.get("status"))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("FastAPI AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public java.util.Map<String, Object> chat(java.util.Map<String, Object> request, String authorizationHeader) {
        String endpoint = aiUrl + "/assistant/chat";
        return postToAssistant(endpoint, request, authorizationHeader);
    }

    @Override
    public java.util.Map<String, Object> compare(java.util.Map<String, Object> request, String authorizationHeader) {
        String endpoint = aiUrl + "/assistant/compare";
        return postToAssistant(endpoint, request, authorizationHeader);
    }

    @Override
    public java.util.Map<String, Object> ask(java.util.Map<String, Object> request, String authorizationHeader) {
        String endpoint = aiUrl + "/assistant/ask";
        return postToAssistant(endpoint, request, authorizationHeader);
    }

    @Override
    public java.util.Map<String, Object> clearMemory(java.util.Map<String, Object> request, String authorizationHeader) {
        String endpoint = aiUrl + "/assistant/clear_memory";
        return postToAssistant(endpoint, request, authorizationHeader);
    }

    private java.util.Map<String, Object> postToAssistant(String endpoint, java.util.Map<String, Object> request, String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
            headers.add("Authorization", authorizationHeader);
        }
        
        log.debug("Forwarding request to FastAPI assistant endpoint: {}", endpoint);
        HttpEntity<java.util.Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
        try {
            return restTemplate.postForObject(endpoint, httpEntity, java.util.Map.class);
        } catch (Exception e) {
            log.error("Failed to forward request to FastAPI at {}: {}", endpoint, e.getMessage());
            throw new RuntimeException("AI Assistant is temporarily unavailable. Please try again later.", e);
        }
    }
}

