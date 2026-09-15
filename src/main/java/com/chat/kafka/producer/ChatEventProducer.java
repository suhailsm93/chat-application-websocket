package com.chat.kafka.producer;

import com.chat.kafka.event.ChatEvents.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.message-events:chat.message-events}")
    private String messageEventsTopic;

    @Value("${app.kafka.topics.delivery-events:chat.delivery-events}")
    private String deliveryEventsTopic;

    @Value("${app.kafka.topics.read-events:chat.read-events}")
    private String readEventsTopic;

    public void publishMessageCreated(MessageCreatedEvent event) {
        // Partition Key = conversation_id guarantees order per conversation
        String partitionKey = event.conversationId().toString();
        log.info("Publishing MessageCreatedEvent: {} for conversation: {}", event.messageId(), partitionKey);
        kafkaTemplate.send(messageEventsTopic, partitionKey, event);
    }

    public void publishDeliveryReceipt(DeliveryEvent event) {
        String partitionKey = event.recipientId().toString();
        kafkaTemplate.send(deliveryEventsTopic, partitionKey, event);
    }

    public void publishReadReceipt(ReadEvent event) {
        String partitionKey = event.conversationId().toString();
        kafkaTemplate.send(readEventsTopic, partitionKey, event);
    }
}