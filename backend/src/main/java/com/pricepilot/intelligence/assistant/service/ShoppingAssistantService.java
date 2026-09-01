package com.pricepilot.intelligence.assistant.service;

import com.pricepilot.intelligence.assistant.dto.*;

import java.util.List;
import java.util.UUID;

public interface ShoppingAssistantService {

    AssistantConversationDTO createConversation(UUID userId, CreateConversationRequest request);

    List<AssistantConversationDTO> listConversations(UUID userId);

    AssistantConversationDTO getConversation(UUID conversationId, UUID userId);

    void deleteConversation(UUID conversationId, UUID userId);

    AssistantResponseDTO sendMessage(UUID conversationId, UUID userId, SendMessageRequest request);

    AssistantResponseDTO processDirectChat(UUID userId, String message, String conversationId);

    AssistantResponseDTO processDirectCompare(UUID userId, List<UUID> productIds, String conversationId);

    AssistantResponseDTO processDirectAsk(UUID userId, String question, String conversationId);

    void clearConversationMemory(UUID userId, String conversationId);
}
