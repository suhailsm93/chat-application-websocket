package com.chat.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record DeliveryEvent(
        UUID messageId,
        UUID conversationId,
        UUID recipientId,
        Instant deliveredAt
) {}