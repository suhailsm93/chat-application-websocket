package com.chat.kafka.consumer;

import com.chat.conversation.repository.ConversationMemberRepository;
import com.chat.kafka.event.MessageCreatedEvent;
import com.chat.kafka.event.DeliveryEvent;
import com.chat.message.model.MessageReceiptEntity;
import com.chat.message.repository.MessageReceiptRepository;
import com.chat.websocket.dto.NodeRoutingEnvelope;
import com.chat.websocket.dto.WebSocketFrames.Frame;
import com.chat.websocket.dto.WebSocketFrames.FrameType;
import com.chat.websocket.registry.RedisConnectionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageConsumer {

    private final ConversationMemberRepository memberRepository;
    private final MessageReceiptRepository receiptRepository;
    private final RedisConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.message-events:chat.message-events}",
            groupId = "chat-delivery-group"
    )
    public void consumeMessageCreated(MessageCreatedEvent event) {
        log.info("Kafka Consumed MessageCreatedEvent: {}", event.messageId());

        memberRepository.findByConversationId(event.conversationId()).forEach(member -> {
            if (!member.getUserId().equals(event.senderId())) {
                try {
                    Frame newMsgFrame = new Frame(
                            FrameType.MESSAGE_NEW,
                            null,
                            event.conversationId(),
                            objectMapper.writeValueAsString(event)
                    );
                    
                    String frameJson = objectMapper.writeValueAsString(newMsgFrame);
                    Map<Object, Object> deviceNodes = connectionRegistry.getUserActiveNodes(member.getUserId());
                    
                    deviceNodes.forEach((deviceIdStr, nodeId) -> {
                        UUID targetDeviceId = UUID.fromString((String) deviceIdStr);
                        NodeRoutingEnvelope envelope = new NodeRoutingEnvelope(targetDeviceId, frameJson);
                        connectionRegistry.routeEnvelopeToNode((String) nodeId, envelope);
                    });
                } catch (Exception e) {
                    log.error("Failed pushing frame to user {}", member.getUserId(), e);
                }
            }
        });
    }

    @KafkaListener(
            topics = "${app.kafka.topics.delivery-events:chat.delivery-events}",
            groupId = "chat-delivery-group"
    )
    @Transactional
    public void consumeDeliveryReceipt(DeliveryEvent event) {
        log.info("Kafka Consumed DeliveryEvent for message: {} by user: {}", event.messageId(), event.recipientId());

        MessageReceiptEntity.MessageReceiptId receiptId = new MessageReceiptEntity.MessageReceiptId(event.messageId(), event.recipientId());
        
        MessageReceiptEntity receipt = receiptRepository.findById(receiptId)
                .orElseGet(() -> MessageReceiptEntity.builder()
                        .messageId(event.messageId())
                        .userId(event.recipientId())
                        .build());

        if (receipt.getDeliveredAt() == null) {
            receipt.setDeliveredAt(event.deliveredAt());
            receiptRepository.save(receipt);
        }
    }
}