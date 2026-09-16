package com.chat.websocket.handler;

import com.chat.common.ratelimit.RedisRateLimiter;
import com.chat.common.util.UUIDv7Utils;
import com.chat.conversation.model.ConversationMember;
import com.chat.conversation.repository.ConversationMemberRepository;
import com.chat.kafka.event.MessageCreatedEvent;
import com.chat.kafka.producer.ChatEventProducer;
import com.chat.message.model.MessageEntity;
import com.chat.message.repository.MessageRepository;
import com.chat.presence.service.PresenceService;
import com.chat.websocket.dto.NodeRoutingEnvelope;
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
    private final ChatEventProducer eventProducer;

    // Local active TCP socket connections: deviceId -> WebSocketSession
    private final Map<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerPubSubListener() {
        connectionRegistry.registerEnvelopeSubscriber("local-handler", envelope -> {
            try {
                dispatchToTargetLocalSession(envelope.targetDeviceId().toString(), envelope.frameJson());
            } catch (Exception e) {
                log.error("Error dispatching envelope locally: ", e);
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = (UUID) session.getAttributes().get("userId");
        UUID deviceId = (UUID) session.getAttributes().get("deviceId");

        if (deviceId != null) {
            localSessions.put(deviceId.toString(), session);
            connectionRegistry.registerLocalConnection(userId, deviceId);
            presenceService.heartbeat(userId);
            broadcastPresence(userId, true);
            log.info("WebSocket connected successfully. User: {}, Device: {}", userId, deviceId);
        } else {
            log.warn("WebSocket connected, but missing deviceId in session attributes.");
        }
    }

    private void broadcastPresence(UUID userId, boolean isOnline) {
        try {
            // Find all conversations this user belongs to
            memberRepository.findByUserId(userId).forEach(member -> {
                Frame presenceFrame = new Frame(
                        FrameType.PRESENCE_CHANGED,
                        null,
                        member.getConversationId(),
                        "{\"userId\":\"" + userId + "\",\"isOnline\":" + isOnline + "}"
                );
                
                // Push update to all other members in that chat
                memberRepository.findByConversationId(member.getConversationId()).forEach(m -> {
                    if (!m.getUserId().equals(userId)) {
                        routeToUserDevices(m.getUserId(), presenceFrame);
                    }
                });
            });
        } catch (Exception e) {
            log.error("Failed to broadcast presence update for user {}", userId, e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID userId = (UUID) session.getAttributes().get("userId");
        UUID deviceId = (UUID) session.getAttributes().get("deviceId");

        if (userId != null) {
            presenceService.heartbeat(userId);
        }

        if (!rateLimiter.isAllowed("ws:msg:" + userId, 30, Duration.ofSeconds(10))) {
            sendError(session, null, "Rate limit exceeded. Please slow down.");
            return;
        }

        Frame frame = objectMapper.readValue(message.getPayload(), Frame.class);

        try {
            switch (frame.type()) {
                case MESSAGE_SEND -> handleMessageSend(session, userId, deviceId, frame);
                case TYPING_START -> handleTyping(userId, frame, true);
                case TYPING_STOP -> handleTyping(userId, frame, false);
                default -> log.warn("Unhandled frame type: {}", frame.type());
            }
        } catch (Exception e) {
            log.error("Error handling WS frame from user {}: ", userId, e);
            sendError(session, frame.requestId(), e.getMessage());
        }
    }

    private void handleMessageSend(WebSocketSession session, UUID senderId, UUID senderDeviceId, Frame frame) throws IOException {
        MessageSendPayload payload = objectMapper.readValue(frame.payloadJson(), MessageSendPayload.class);

        boolean isMember = memberRepository.existsById(
                new ConversationMember.ConversationMemberId(frame.conversationId(), senderId)
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

        // 1. Immediate ACK back to the sender device
        MessageAckPayload ackPayload = new MessageAckPayload(
                messageId, frame.conversationId(), payload.clientMessageId(), now, "SENT"
        );
        Frame ackFrame = new Frame(
                FrameType.MESSAGE_ACK, frame.requestId(), frame.conversationId(), objectMapper.writeValueAsString(ackPayload)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackFrame)));

        // 2. Self-Sync: Dispatch MESSAGE_NEW mirror frame to sender's OTHER devices
        Frame mirrorFrame = new Frame(
                FrameType.MESSAGE_NEW, null, frame.conversationId(), objectMapper.writeValueAsString(messageEntity)
        );
        routeToUserDevicesExceptOrigin(senderId, senderDeviceId, mirrorFrame);

        // 3. Publish to Kafka Backbone for recipient delivery
        MessageCreatedEvent kafkaEvent = new MessageCreatedEvent(
                messageId,
                frame.conversationId(),
                senderId,
                payload.clientMessageId(),
                payload.messageType(),
                payload.content(),
                payload.replyToMessageId(),
                now
        );
        eventProducer.publishMessageCreated(kafkaEvent);
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

    public void routeToUserDevices(UUID targetUserId, Frame frame) {
        try {
            String frameJson = objectMapper.writeValueAsString(frame);
            Map<Object, Object> deviceNodeMap = connectionRegistry.getUserActiveNodes(targetUserId);

            deviceNodeMap.forEach((deviceIdStr, targetNodeId) -> {
                UUID targetDeviceId = UUID.fromString((String) deviceIdStr);
                NodeRoutingEnvelope envelope = new NodeRoutingEnvelope(targetDeviceId, frameJson);
                connectionRegistry.routeEnvelopeToNode((String) targetNodeId, envelope);
            });
        } catch (Exception e) {
            log.error("Failed routing frame to user: {}", targetUserId, e);
        }
    }

    private void routeToUserDevicesExceptOrigin(UUID userId, UUID originDeviceId, Frame frame) {
        try {
            String frameJson = objectMapper.writeValueAsString(frame);
            Map<Object, Object> deviceNodeMap = connectionRegistry.getUserActiveNodes(userId);

            deviceNodeMap.forEach((deviceIdStr, targetNodeId) -> {
                if (!deviceIdStr.equals(originDeviceId.toString())) {
                    UUID targetDeviceId = UUID.fromString((String) deviceIdStr);
                    NodeRoutingEnvelope envelope = new NodeRoutingEnvelope(targetDeviceId, frameJson);
                    connectionRegistry.routeEnvelopeToNode((String) targetNodeId, envelope);
                }
            });
        } catch (Exception e) {
            log.error("Failed routing envelope to secondary devices of user {}", userId, e);
        }
    }

    private void dispatchToTargetLocalSession(String targetDeviceId, String frameJson) throws IOException {
        WebSocketSession session = localSessions.get(targetDeviceId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(frameJson));
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
            localSessions.remove(deviceId.toString());
            connectionRegistry.unregisterLocalConnection(userId, deviceId);
        }

        if (userId != null && connectionRegistry.getUserActiveNodes(userId).isEmpty()) {
            presenceService.setOffline(userId);
            broadcastPresence(userId, false);
        }
        log.info("WebSocket disconnected. User: {}, Device: {}", userId, deviceId);
    }
}