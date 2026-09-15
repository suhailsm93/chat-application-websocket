package com.chat.kafka.event;

import com.chat.message.model.MessageEntity.MessageType;
import java.time.Instant;
import java.util.UUID;

public class ChatEvents {

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

    public record DeliveryEvent(
            UUID messageId,
            UUID conversationId,
            UUID recipientId,
            Instant deliveredAt
    ) {}

    public record ReadEvent(
            UUID messageId,
            UUID conversationId,
            UUID readerId,
            Instant readAt
    ) {}
}