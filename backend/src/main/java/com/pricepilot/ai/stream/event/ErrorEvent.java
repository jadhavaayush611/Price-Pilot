package com.pricepilot.ai.stream.event;

import lombok.Getter;
import lombok.Setter;

/**
 * Event: error (Terminal)
 * Emitted on fatal protocol, persistence, or authorization errors.
 */
@Getter
@Setter
public class ErrorEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "error";

    private String errorCode;
    private String message;
    private boolean fatal = true;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
