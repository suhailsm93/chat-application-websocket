package com.chat.websocket.handler;

import com.chat.common.util.UUIDv7Utils;
import com.chat.conversation.repository.ConversationMemberRepository;
import com.chat.message.model.MessageEntity;
import com.chat.message.repository.MessageRepository;
import com.chat.websocket.dto.WebSocketFrames.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final MessageRepository messageRepository;
    private final ConversationMemberRepository memberRepository;

    // Local-only mapping for Phase 2: [userId -> [deviceId -> session]]
    private final Map<UUID, Map<UUID, WebSocketSession>> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        UUID deviceId = (UUID) session.getAttributes().get("deviceId");

        activeSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(deviceId, session);
        log.info("WebSocket connected: User ID: {}, Device ID: {}", userId, deviceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID userId = (UUID) session.getAttributes().get("userId");
        Frame frame = objectMapper.readValue(message.getPayload(), Frame.class);

        try {
            switch (frame.type()) {
                case MESSAGE_SEND -> handleMessageSend(session, userId, frame);
                default -> log.warn("Unsupported frame type in Phase 2: {}", frame.type());
            }
        } catch (Exception e) {
            log.error("Error processing frame: ", e);
            sendError(session, frame.requestId(), e.getMessage());
        }
    }

    private void handleMessageSend(WebSocketSession session, UUID senderId, Frame frame) throws IOException {
        MessageSendPayload payload = objectMapper.readValue(frame.payloadJson(), MessageSendPayload.class);

        // Security check: Verify sender is a member of the conversation
        boolean isMember = memberRepository.existsById(
                new com.chat.conversation.model.ConversationMember.ConversationMemberId(frame.conversationId(), senderId)
        );
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this conversation.");
        }

        // Generate time-sortable UUIDv7
        UUID messageId = UUIDv7Utils.generateUUIDv7();
        Instant now = Instant.now();

        // Persist message directly (durable write-through)
        MessageEntity messageEntity = MessageEntity.builder()
                .id(messageId)
                .conversationId(frame.conversationId())
                .senderId(senderId)
                .clientMessageId(payload.clientMessageId())
                .messageType(payload.messageType())
                .content(payload.content())
                .replyToMessageId(payload.replyToMessageId())
                .isEdited(false)
                .isDeleted(false)
                .createdAt(now)
                .build();

        messageRepository.save(messageEntity);

        // Return immediate MESSAGE_ACK to the sender
        MessageAckPayload ackPayload = new MessageAckPayload(
                messageId,
                frame.conversationId(),
                payload.clientMessageId(),
                now,
                "SENT"
        );

        Frame ackFrame = new Frame(
                FrameType.MESSAGE_ACK,
                frame.requestId(),
                frame.conversationId(),
                objectMapper.writeValueAsString(ackPayload)
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackFrame)));
        
        // Phase 2: Simple local delivery to online recipients (Kafka and Redis pub/sub are added in Phase 3/4)
        deliverLocally(messageEntity);
    }

    private void deliverLocally(MessageEntity message) throws IOException {
        memberRepository.findByConversationId(message.getConversationId()).forEach(member -> {
            if (!member.getUserId().equals(message.getSenderId())) {
                Map<UUID, WebSocketSession> devices = activeSessions.get(member.getUserId());
                if (devices != null) {
                    devices.values().forEach(session -> {
                        try {
                            if (session.isOpen()) {
                                Frame newMsgFrame = new Frame(
                                        FrameType.MESSAGE_NEW,
                                        null,
                                        message.getConversationId(),
                                        objectMapper.writeValueAsString(message)
                                );
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(newMsgFrame)));
                            }
                        } catch (IOException e) {
                            log.error("Failed to push message locally: ", e);
                        }
                    });
                }
            }
        });
    }

    private void sendError(WebSocketSession session, String requestId, String errorMsg) throws IOException {
        ErrorPayload errorPayload = new ErrorPayload(errorMsg);
        Frame errorFrame = new Frame(
                FrameType.ERROR,
                requestId,
                null,
                objectMapper.writeValueAsString(errorPayload)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorFrame)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        UUID deviceId = (UUID) session.getAttributes().get("deviceId");

        if (userId != null && deviceId != null) {
            Map<UUID, WebSocketSession> devices = activeSessions.get(userId);
            if (devices != null) {
                devices.remove(deviceId);
                if (devices.isEmpty()) {
                    activeSessions.remove(userId);
                }
            }
            log.info("WebSocket disconnected: User ID: {}, Device ID: {}", userId, deviceId);
        }
    }
}