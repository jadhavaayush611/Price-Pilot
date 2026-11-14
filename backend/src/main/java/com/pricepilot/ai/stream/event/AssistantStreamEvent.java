package com.pricepilot.ai.stream.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base abstract class for all Server-Sent Events conforming to the v1.4 contract.
 */
@Getter
@Setter
public abstract class AssistantStreamEvent {

    public static final String PROTOCOL_VERSION = "v1.4";

    private String protocolVersion = PROTOCOL_VERSION;
    private int sequenceNumber;
    private UUID conversationId;
    private UUID messageId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant timestamp = Instant.now();

    @JsonIgnore
    public abstract String getEventName();

    @JsonIgnore
    public abstract boolean isTerminal();
}
