package com.chat.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record ReadEvent(
        UUID messageId,
        UUID conversationId,
        UUID readerId,
        Instant readAt
) {}