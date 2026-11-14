package com.pricepilot.ai.stream;

import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Immutable session context capturing identifiers and parameters for an active stream.
 */
@Getter
@Builder
public class StreamSessionContext {
    private final UUID conversationId;
    private final UUID userId;
    private final UUID userMessageId;
    private final UUID assistantMessageId;
    private final String rawContent;
    private final String sanitizedContent;
    private final UUID activeProductId;
    private final AssistantIntent intent;
}
