package com.chat.kafka.event;

import com.chat.message.model.MessageEntity.MessageType;
import java.time.Instant;
import java.util.UUID;

public record MessageCreatedEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        String clientMessageId,
        MessageType messageType,
        String content,
        UUID replyToMessageId,
        Instant createdAt
) {}