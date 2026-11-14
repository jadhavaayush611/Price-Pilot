package com.pricepilot.ai.stream.event;

import lombok.Getter;
import lombok.Setter;

/**
 * Event: message_interrupted (Terminal)
 * Emitted when generation halts after one or more text_delta events have been emitted.
 */
@Getter
@Setter
public class MessageInterruptedEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "message_interrupted";

    private String reason;
    private int tokensEmitted;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
