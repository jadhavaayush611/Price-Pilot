package com.pricepilot.ai.stream.event;

import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import lombok.Getter;
import lombok.Setter;

/**
 * Event: message_start
 * Emitted immediately after authentication and intent classification.
 */
@Getter
@Setter
public class MessageStartEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "message_start";

    private String role = "assistant";
    private AssistantIntent intent;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}
