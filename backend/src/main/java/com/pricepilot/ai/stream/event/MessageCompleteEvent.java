package com.pricepilot.ai.stream.event;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Event: message_complete (Terminal)
 * Emitted upon successful stream completion following database persistence.
 */
@Getter
@Setter
public class MessageCompleteEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "message_complete";

    private int totalTokens;
    private List<String> suggestedPrompts = new ArrayList<>();
    private Long durationMs;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
