package com.pricepilot.ai.stream.event;

import lombok.Getter;
import lombok.Setter;

/**
 * Event: fallback (Non-terminal informational event)
 * Emitted when upstream inference encounters issues before the first token delta.
 * May be followed by subsequent non-terminal events and exactly one terminal event.
 */
@Getter
@Setter
public class FallbackEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "fallback";

    private boolean fallbackMode = true;
    private String reason;
    private String response;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}
