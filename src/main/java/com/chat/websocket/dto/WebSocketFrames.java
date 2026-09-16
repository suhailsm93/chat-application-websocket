package com.chat.websocket.dto;

import com.chat.message.model.MessageEntity.MessageType;
import java.time.Instant;
import java.util.UUID;

public class WebSocketFrames {

    public enum FrameType {
        // Client to Server
        MESSAGE_SEND,
        ACK_DELIVERY,
        ACK_READ,
        TYPING_START,
        TYPING_STOP,

        // Server to Client
        MESSAGE_ACK,
        MESSAGE_NEW,
        PRESENCE_CHANGED,
        ERROR
    }

    public record Frame(
            FrameType type,
            String requestId,
            UUID conversationId,
            String payloadJson // Custom parsing based on type to keep it flexible
    ) {}

    public record MessageSendPayload(
            String clientMessageId,
            MessageType messageType,
            String content,
            UUID replyToMessageId
    ) {}

    public record MessageAckPayload(
            UUID messageId,
            UUID conversationId,
            String clientMessageId,
            Instant createdAt,
            String status // "SENT"
    ) {}
    
    public record ErrorPayload(
            String message
    ) {}
}