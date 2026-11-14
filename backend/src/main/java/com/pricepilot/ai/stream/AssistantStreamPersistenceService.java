package com.pricepilot.ai.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.assistant.dto.AssistantEvidenceBundle;
import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import com.pricepilot.intelligence.assistant.dto.SendMessageRequest;
import com.pricepilot.intelligence.assistant.intent.PromptInjectionProtector;
import com.pricepilot.intelligence.assistant.intent.ShoppingIntentClassifier;
import com.pricepilot.intelligence.assistant.model.AssistantConversationEntity;
import com.pricepilot.intelligence.assistant.model.AssistantMessageEntity;
import com.pricepilot.intelligence.assistant.model.MessageRole;
import com.pricepilot.intelligence.assistant.repository.AssistantConversationRepository;
import com.pricepilot.intelligence.assistant.repository.AssistantMessageRepository;
import com.pricepilot.intelligence.assistant.service.ShoppingAssistantService;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manages explicit short-lived database transaction boundaries for streaming requests.
 * Ensures database transactions are never held open across long-running HTTP streaming.
 */
@Service
public class AssistantStreamPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AssistantStreamPersistenceService.class);

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ShoppingAssistantService assistantService;
    private final PromptInjectionProtector promptProtector;
    private final ShoppingIntentClassifier intentClassifier;
    private final ObjectMapper objectMapper;

    public AssistantStreamPersistenceService(
            AssistantConversationRepository conversationRepository,
            AssistantMessageRepository messageRepository,
            UserRepository userRepository,
            ShoppingAssistantService assistantService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PromptInjectionProtector promptProtector,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ShoppingIntentClassifier intentClassifier,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.assistantService = assistantService;
        this.promptProtector = promptProtector;
        this.intentClassifier = intentClassifier;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Synchronous ownership validation on caller thread before executor admission.
     */
    @Transactional(readOnly = true)
    public void validateOwnership(UUID conversationId, UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new AccessDeniedException("Authenticated user not found in repository");
        }
        assistantService.validateConversationOwnership(conversationId, userId);
    }

    /**
     * Boundary 1: Persist the user's message in a short transaction after executor admission.
     * Does NOT persist an empty assistant message placeholder, avoiding orphan assistant records.
     */
    @Transactional
    public StreamSessionContext initializeSession(UUID conversationId, UUID userId, SendMessageRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new AccessDeniedException("Authenticated user not found in repository");
        }

        assistantService.validateConversationOwnership(conversationId, userId);

        AssistantConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        String rawContent = request.getContent();
        String sanitizedContent = (promptProtector != null)
                ? promptProtector.sanitizeUserInput(rawContent)
                : rawContent;

        AssistantMessageEntity userMessage = AssistantMessageEntity.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(rawContent)
                .createdAt(LocalDateTime.now())
                .build();
        userMessage = messageRepository.save(userMessage);

        AssistantIntent intent = (intentClassifier != null)
                ? intentClassifier.classifyIntent(sanitizedContent)
                : AssistantIntent.DISCOVERY;

        UUID assistantMessageId = UUID.randomUUID();

        return StreamSessionContext.builder()
                .conversationId(conversationId)
                .userId(userId)
                .userMessageId(userMessage.getId())
                .assistantMessageId(assistantMessageId)
                .rawContent(rawContent)
                .sanitizedContent(sanitizedContent)
                .activeProductId(request.getActiveProductId())
                .intent(intent)
                .build();
    }

    /**
     * Boundary 2: Build grounded evidence bundle in a short read-only transaction.
     */
    @Transactional(readOnly = true)
    public AssistantEvidenceBundle prepareEvidenceBundle(StreamSessionContext context) {
        return assistantService.createEvidenceBundle(
                context.getConversationId(),
                context.getUserId(),
                context.getSanitizedContent(),
                context.getActiveProductId(),
                context.getIntent()
        );
    }

    /**
     * Boundary 4: Persist the finalized assistant message in a dedicated short transaction.
     * Uses the 2.0-second persistence reserve budget.
     */
    @Transactional(timeout = 2, propagation = Propagation.REQUIRES_NEW)
    public AssistantMessageEntity finalizeAssistantMessage(
            UUID conversationId,
            UUID assistantMessageId,
            AssistantIntent intent,
            String content,
            AssistantEvidenceBundle bundle) {

        AssistantConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        AssistantMessageEntity assistantMessage = null;
        if (assistantMessageId != null) {
            assistantMessage = messageRepository.findById(assistantMessageId).orElse(null);
        }

        if (assistantMessage == null) {
            assistantMessage = AssistantMessageEntity.builder()
                    .conversation(conversation)
                    .role(MessageRole.ASSISTANT)
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        String serializedBundle = serializeJson(bundle);
        String serializedPayload = bundle != null ? serializeJson(bundle.getGroundedProducts()) : null;

        assistantMessage.setContent(content != null ? content : "");
        assistantMessage.setIntent(intent);
        assistantMessage.setEvidenceBundle(serializedBundle);
        assistantMessage.setPayload(serializedPayload);

        assistantMessage = messageRepository.save(assistantMessage);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return assistantMessage;
    }

    private String serializeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize assistant stream payload: {}", e.getMessage());
            return null;
        }
    }
}
