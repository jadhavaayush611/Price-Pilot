package com.pricepilot.ai.stream.event;

import com.pricepilot.intelligence.assistant.dto.AssistantEvidenceBundle;
import lombok.Getter;
import lombok.Setter;

/**
 * Event: evidence
 * Emitted when grounded products and evidence are assembled.
 */
@Getter
@Setter
public class EvidenceEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "evidence";

    private AssistantEvidenceBundle evidenceBundle;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}
