package com.chat.websocket.dto;

import java.io.Serializable;
import java.util.UUID;

public record NodeRoutingEnvelope(
        UUID targetDeviceId,
        String frameJson
) implements Serializable {}