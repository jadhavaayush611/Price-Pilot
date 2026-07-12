package com.pricepilot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${pricepilot.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${pricepilot.rate-limit.auth.limit:10}")
    private int authLimit;

    @Value("${pricepilot.rate-limit.ai.limit:20}")
    private int aiLimit;

    @Value("${pricepilot.rate-limit.recommendation.limit:30}")
    private int recommendationLimit;

    // Stores IP-based token buckets: URI_prefix -> IP -> TokenBucket
    private final Map<String, Map<String, TokenBucket>> limiters = new ConcurrentHashMap<>();

    private static class TokenBucket {
        private final double capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(double capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTime = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillTime) / 1e9;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillRatePerSecond);
            lastRefillTime = now;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        String uri = request.getRequestURI();
        String clientIp = getClientIp(request);
        
        String key;
        int limitPerMinute;

        if (uri.startsWith("/api/v1/auth")) {
            key = "auth";
            limitPerMinute = authLimit;
        } else if (uri.startsWith("/api/v1/assistant")) {
            key = "ai";
            limitPerMinute = aiLimit;
        } else if (uri.startsWith("/api/v1/recommendations")) {
            key = "recommendation";
            limitPerMinute = recommendationLimit;
        } else {
            return true;
        }

        Map<String, TokenBucket> ipMap = limiters.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        double refillRate = limitPerMinute / 60.0;
        TokenBucket bucket = ipMap.computeIfAbsent(clientIp, ip -> new TokenBucket(limitPerMinute, refillRate));

        if (!bucket.tryConsume()) {
            org.slf4j.Logger auditLog = org.slf4j.LoggerFactory.getLogger("AuditLogger");
            auditLog.warn("AUDIT: RATE_LIMIT_EXCEEDED | client_ip={} | endpoint={}", clientIp, uri);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again later.\"}");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
