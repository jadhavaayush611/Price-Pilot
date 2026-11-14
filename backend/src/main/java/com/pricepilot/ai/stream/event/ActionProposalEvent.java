package com.pricepilot.ai.stream.event;

import lombok.Getter;
import lombok.Setter;

/**
 * Event: action_proposal
 * Emitted when non-mutating UI action proposals are suggested.
 */
@Getter
@Setter
public class ActionProposalEvent extends AssistantStreamEvent {

    public static final String EVENT_NAME = "action_proposal";

    private Object proposal;

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }
}
