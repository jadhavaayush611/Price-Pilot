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
}
