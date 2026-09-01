package com.pricepilot.ai;

import com.pricepilot.intelligence.assistant.dto.*;
import com.pricepilot.intelligence.assistant.service.ShoppingAssistantService;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/assistant")
@CrossOrigin(origins = "*")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final ShoppingAssistantService assistantService;
    private final UserRepository userRepository;
    private final AiClient aiClient;

    public AssistantController(
            ShoppingAssistantService assistantService,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AiClient aiClient) {
        this.assistantService = assistantService;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
    }

    // =========================================================================
    // Versioned Conversation APIs (Phase 9)
    // =========================================================================

    @PostMapping("/conversations")
    public ResponseEntity<AssistantConversationDTO> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request) {

        UUID userId = resolveAuthenticatedUserId(principal);
        AssistantConversationDTO conversation = assistantService.createConversation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<AssistantConversationDTO>> listConversations(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = resolveAuthenticatedUserId(principal);
        List<AssistantConversationDTO> list = assistantService.listConversations(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<AssistantConversationDTO> getConversation(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = resolveAuthenticatedUserId(principal);
        AssistantConversationDTO conversation = assistantService.getConversation(id, userId);
        return ResponseEntity.ok(conversation);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = resolveAuthenticatedUserId(principal);
        assistantService.deleteConversation(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<AssistantResponseDTO> sendMessage(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SendMessageRequest request) {

        UUID userId = resolveAuthenticatedUserId(principal);
        AssistantResponseDTO response = assistantService.sendMessage(id, userId, request);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Backward-Compatible Assistant APIs (Preserved for SDK & Existing Clients)
    // =========================================================================

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UUID userId = resolveAuthenticatedUserId(principal);
        try {
            if (aiClient != null) {
                Map<String, Object> aiRes = aiClient.chat(request, authHeader);
                if (aiRes != null && !aiRes.isEmpty() && aiRes.containsKey("response")) {
                    return ResponseEntity.ok(aiRes);
                }
            }
        } catch (Exception e) {
            log.info("Direct AI chat unavailable, using deterministic shopping assistant: {}", e.getMessage());
        }

        String message = (String) request.getOrDefault("message", "");
        String convId = (String) request.get("conversationId");

        AssistantResponseDTO response = assistantService.processDirectChat(userId, message, convId);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> chat(UserPrincipal principal, Map<String, Object> request) {
        return chat(principal, request, null);
    }

    @PostMapping("/compare")
    public ResponseEntity<?> compare(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UUID userId = resolveAuthenticatedUserId(principal);
        try {
            if (aiClient != null) {
                Map<String, Object> aiRes = aiClient.compare(request, authHeader);
                if (aiRes != null && !aiRes.isEmpty() && aiRes.containsKey("response")) {
                    return ResponseEntity.ok(aiRes);
                }
            }
        } catch (Exception e) {
            log.info("Direct AI compare unavailable, using deterministic shopping assistant: {}", e.getMessage());
        }

        Object rawProductIds = request.get("productIds");
        List<UUID> productIds = new ArrayList<>();
        if (rawProductIds instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    try {
                        productIds.add(UUID.fromString(item.toString()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        String convId = (String) request.get("conversationId");

        AssistantResponseDTO response = assistantService.processDirectCompare(userId, productIds, convId);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> compare(UserPrincipal principal, Map<String, Object> request) {
        return compare(principal, request, null);
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UUID userId = resolveAuthenticatedUserId(principal);
        try {
            if (aiClient != null) {
                Map<String, Object> aiRes = aiClient.ask(request, authHeader);
                if (aiRes != null && !aiRes.isEmpty() && aiRes.containsKey("response")) {
                    return ResponseEntity.ok(aiRes);
                }
            }
        } catch (Exception e) {
            log.info("Direct AI ask unavailable, using deterministic shopping assistant: {}", e.getMessage());
        }

        String question = (String) request.getOrDefault("question", request.getOrDefault("message", ""));
        String convId = (String) request.get("conversationId");

        AssistantResponseDTO response = assistantService.processDirectAsk(userId, question, convId);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> ask(UserPrincipal principal, Map<String, Object> request) {
        return ask(principal, request, null);
    }

    @PostMapping("/clear_memory")
    public ResponseEntity<Map<String, Object>> clearMemory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UUID userId = resolveAuthenticatedUserId(principal);
        String convId = (String) request.get("conversationId");

        try {
            if (aiClient != null) {
                Map<String, Object> aiRes = aiClient.clearMemory(request, authHeader);
                if (aiRes != null && !aiRes.isEmpty()) {
                    assistantService.clearConversationMemory(userId, convId);
                    return ResponseEntity.ok(aiRes);
                }
            }
        } catch (Exception e) {
            log.info("Direct AI clearMemory call failed: {}", e.getMessage());
        }

        assistantService.clearConversationMemory(userId, convId);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Memory cleared");
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> clearMemory(UserPrincipal principal, Map<String, Object> request) {
        return clearMemory(principal, request, null);
    }

    // =========================================================================
    // Security Identity Resolution Helper (Principal-Driven)
    // =========================================================================

    private UUID resolveAuthenticatedUserId(UserPrincipal principal) {
        if (principal != null && principal.getId() != null) {
            return principal.getId();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to access shopping assistant");
        }

        Object p = authentication.getPrincipal();
        if (p instanceof UserPrincipal up) {
            return up.getId();
        } else if (p instanceof UserEntity ue) {
            return ue.getId();
        } else if (p instanceof UserDetails ud) {
            return userRepository.findByEmail(ud.getUsername())
                    .map(UserEntity::getId)
                    .orElseGet(() -> {
                        UserEntity created = userRepository.save(UserEntity.builder()
                                .email(ud.getUsername())
                                .firstName("Test")
                                .lastName("User")
                                .password("Password123!")
                                .role(com.pricepilot.user.Role.USER)
                                .enabled(true)
                                .locked(false)
                                .build());
                        return created.getId();
                    });
        } else if (p instanceof String s && !"anonymousUser".equals(s)) {
            return userRepository.findByEmail(s)
                    .map(UserEntity::getId)
                    .orElseGet(() -> {
                        UserEntity created = userRepository.save(UserEntity.builder()
                                .email(s)
                                .firstName("Test")
                                .lastName("User")
                                .password("Password123!")
                                .role(com.pricepilot.user.Role.USER)
                                .enabled(true)
                                .locked(false)
                                .build());
                        return created.getId();
                    });
        }

        throw new AccessDeniedException("Authentication required to access shopping assistant");
    }
}
