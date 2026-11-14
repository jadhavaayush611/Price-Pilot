package com.pricepilot.ai.stream.event;

import lombok.Getter;
import lombok.Setter;

/**
 * Event: text_delta
 * Emitted incrementally for each generated token chunk.
 */
@Getter
@Setter
public class TextDeltaEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "text_delta";

    private String delta;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}
