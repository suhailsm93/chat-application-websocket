package com.chat.message.service;

import com.chat.kafka.event.DeliveryEvent;
import com.chat.kafka.producer.ChatEventProducer;
import com.chat.message.dto.SyncDtos.SyncResponse;
import com.chat.message.model.MessageEntity;
import com.chat.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncService {

    private final MessageRepository messageRepository;
    private final ChatEventProducer eventProducer;

    @Transactional
    public SyncResponse syncMissedMessages(UUID userId, UUID recipientDeviceId, UUID afterMessageId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 200));
        List<MessageEntity> missedMessages = messageRepository.findMissedMessagesForUser(userId, afterMessageId, pageable);

        Instant now = Instant.now();

        // Publish delivery events for all synced offline messages
        for (MessageEntity msg : missedMessages) {
            DeliveryEvent deliveryEvent = new DeliveryEvent(
                    msg.getId(),
                    msg.getConversationId(),
                    userId,
                    now
            );
            eventProducer.publishDeliveryReceipt(deliveryEvent);
        }

        UUID lastId = missedMessages.isEmpty() ? afterMessageId : missedMessages.get(missedMessages.size() - 1).getId();
        boolean hasMore = missedMessages.size() == pageable.getPageSize();

        log.info("User {} synced {} missed messages after ID {}", userId, missedMessages.size(), afterMessageId);

        return new SyncResponse(missedMessages, lastId, hasMore);
    }
}