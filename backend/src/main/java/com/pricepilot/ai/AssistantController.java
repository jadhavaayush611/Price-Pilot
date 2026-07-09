package com.pricepilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assistant")
@CrossOrigin(origins = "*")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final AiClient aiClient;

    public AssistantController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        
        log.info("Received AI assistant chat request");
        return handleForward("chat", request, authorizationHeader);
    }

    @PostMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        
        log.info("Received AI assistant compare request");
        return handleForward("compare", request, authorizationHeader);
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        
        log.info("Received AI assistant ask request");
        return handleForward("ask", request, authorizationHeader);
    }

    @PostMapping("/clear_memory")
    public ResponseEntity<Map<String, Object>> clearMemory(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        
        log.info("Received AI assistant clear_memory request");
        return handleForward("clear_memory", request, authorizationHeader);
    }

    private ResponseEntity<Map<String, Object>> handleForward(
            String action, Map<String, Object> request, String authorizationHeader) {
        
        String email = getAuthenticatedUserEmail();
        if (email == null) {
            log.warn("Unauthorized attempt to access AI assistant - action: {}", action);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Enrich the request with user info to help ground context in memory
        Map<String, Object> enrichedRequest = new HashMap<>(request);
        enrichedRequest.put("email", email);

        try {
            Map<String, Object> response;
            if ("chat".equals(action)) {
                response = aiClient.chat(enrichedRequest, authorizationHeader);
            } else if ("compare".equals(action)) {
                response = aiClient.compare(enrichedRequest, authorizationHeader);
            } else if ("ask".equals(action)) {
                response = aiClient.ask(enrichedRequest, authorizationHeader);
            } else {
                response = aiClient.clearMemory(enrichedRequest, authorizationHeader);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI assistant service error on {}: {}", action, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "AI Assistant Service Error");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    private String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            String name = principal.toString();
            if ("anonymousUser".equals(name)) {
                return null;
            }
            return name;
        }
    }
}
