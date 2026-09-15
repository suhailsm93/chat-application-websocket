package com.chat.websocket.handler;

import com.chat.common.ratelimit.RedisRateLimiter;
import com.chat.common.util.UUIDv7Utils;
import com.chat.conversation.repository.ConversationMemberRepository;
import com.chat.message.model.MessageEntity;
import com.chat.message.repository.MessageRepository;
import com.chat.presence.service.PresenceService;
import com.chat.websocket.dto.WebSocketFrames.*;
import com.chat.websocket.registry.RedisConnectionRegistry;
import com.chat.websocket.service.TypingIndicatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
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
    private final RedisConnectionRegistry connectionRegistry;
    private final PresenceService presenceService;
    private final TypingIndicatorService typingIndicatorService;
    private final RedisRateLimiter rateLimiter;

    // Local sessions map: deviceId -> WebSocketSession
    private final Map<UUID, WebSocketSession> localSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerPubSubListener() {
        connectionRegistry.registerFrameSubscriber("local-handler", (nodeId, frameJson) -> {
            try {
                Frame frame = objectMapper.readValue(frameJson, Frame.class);
                dispatchToLocalSession(frame);
            } catch (Exception e) {
                log.error("Error dispatching pub/sub frame locally: ", e);
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        UUID deviceId = (UUID) session.getAttributes().get("deviceId");

        localSessions.put(deviceId, session);
        connectionRegistry.registerLocalConnection(userId, deviceId);
        presenceService.heartbeat(userId);

        log.info("WebSocket connection established. User: {}, Device: {}", userId, deviceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID userId = (UUID) session.getAttributes().get("userId");
        presenceService.heartbeat(userId); // Refresh presence TTL on frame

        // Rate limiting check: 30 frames per 10 seconds
        if (!rateLimiter.isAllowed("ws:msg:" + userId, 30, Duration.ofSeconds(10))) {
            sendError(session, null, "Rate limit exceeded. Please slow down.");
            return;
        }

        Frame frame = objectMapper.readValue(message.getPayload(), Frame.class);

        try {
            switch (frame.type()) {
                case MESSAGE_SEND -> handleMessageSend(session, userId, frame);
                case TYPING_START -> handleTyping(userId, frame, true);
                case TYPING_STOP -> handleTyping(userId, frame, false);
                default -> log.warn("Unhandled frame type: {}", frame.type());
            }
        } catch (Exception e) {
            log.error("Error handling WS frame: ", e);
            sendError(session, frame.requestId(), e.getMessage());
        }
    }

    private void handleMessageSend(WebSocketSession session, UUID senderId, Frame frame) throws IOException {
        MessageSendPayload payload = objectMapper.readValue(frame.payloadJson(), MessageSendPayload.class);

        boolean isMember = memberRepository.existsById(
                new com.chat.conversation.model.ConversationMember.ConversationMemberId(frame.conversationId(), senderId)
        );
        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this conversation.");
        }

        UUID messageId = UUIDv7Utils.generateUUIDv7();
        Instant now = Instant.now();

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

        // Immediate ACK back to sender session
        MessageAckPayload ackPayload = new MessageAckPayload(
                messageId, frame.conversationId(), payload.clientMessageId(), now, "SENT"
        );
        Frame ackFrame = new Frame(
                FrameType.MESSAGE_ACK, frame.requestId(), frame.conversationId(), objectMapper.writeValueAsString(ackPayload)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackFrame)));

        // Route message across node cluster to recipient devices
        broadcastToConversationMembers(messageEntity);
    }

    private void handleTyping(UUID userId, Frame frame, boolean isTyping) throws IOException {
        typingIndicatorService.setTyping(frame.conversationId(), userId, isTyping);
        
        Frame typingFrame = new Frame(
                isTyping ? FrameType.TYPING_START : FrameType.TYPING_STOP,
                null,
                frame.conversationId(),
                "{\"userId\":\"" + userId + "\"}"
        );

        memberRepository.findByConversationId(frame.conversationId()).forEach(member -> {
            if (!member.getUserId().equals(userId)) {
                routeToUserDevices(member.getUserId(), typingFrame);
            }
        });
    }

    private void broadcastToConversationMembers(MessageEntity message) throws IOException {
        Frame newMsgFrame = new Frame(
                FrameType.MESSAGE_NEW, null, message.getConversationId(), objectMapper.writeValueAsString(message)
        );

        memberRepository.findByConversationId(message.getConversationId()).forEach(member -> {
            if (!member.getUserId().equals(message.getSenderId())) {
                routeToUserDevices(member.getUserId(), newMsgFrame);
            }
        });
    }

    private void routeToUserDevices(UUID targetUserId, Frame frame) {
        try {
            String frameJson = objectMapper.writeValueAsString(frame);
            Map<Object, Object> deviceNodeMap = connectionRegistry.getUserActiveNodes(targetUserId);

            deviceNodeMap.forEach((deviceIdStr, targetNodeId) -> {
                connectionRegistry.routeFrameToNode((String) targetNodeId, frameJson);
            });
        } catch (Exception e) {
            log.error("Failed to route frame to user: {}", targetUserId, e);
        }
    }

    private void dispatchToLocalSession(Frame frame) throws IOException {
        String frameJson = objectMapper.writeValueAsString(frame);
        TextMessage textMessage = new TextMessage(frameJson);

        for (WebSocketSession session : localSessions.values()) {
            if (session.isOpen()) {
                session.sendMessage(textMessage);
            }
        }
    }

    private void sendError(WebSocketSession session, String requestId, String errorMsg) throws IOException {
        ErrorPayload errorPayload = new ErrorPayload(errorMsg);
        Frame errorFrame = new Frame(FrameType.ERROR, requestId, null, objectMapper.writeValueAsString(errorPayload));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorFrame)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        UUID deviceId = (UUID) session.getAttributes().get("deviceId");

        if (deviceId != null) {
            localSessions.remove(deviceId);
            connectionRegistry.unregisterLocalConnection(userId, deviceId);
        }

        if (userId != null && localSessions.isEmpty()) {
            presenceService.setOffline(userId);
        }
        log.info("WebSocket disconnected. User: {}, Device: {}", userId, deviceId);
    }
}