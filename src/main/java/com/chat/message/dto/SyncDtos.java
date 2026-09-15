package com.chat.message.dto;

import com.chat.message.model.MessageEntity;

import java.util.List;
import java.util.UUID;

public class SyncDtos {

    public record SyncResponse(
            List<MessageEntity> missedMessages,
            UUID lastEvaluatedMessageId,
            boolean hasMore
    ) {}
}