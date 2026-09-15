package com.chat.message.service;

import com.chat.common.exception.ResourceNotFoundException;
import com.chat.conversation.repository.ConversationMemberRepository;
import com.chat.message.model.MessageEntity;
import com.chat.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private final MessageRepository messageRepository;
    private final ConversationMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<MessageEntity> getConversationMessages(UUID userId, UUID conversationId, UUID beforeMessageId, int limit) {
        // Confirm user is authorized to read history
        boolean isMember = memberRepository.existsById(
                new com.chat.conversation.model.ConversationMember.ConversationMemberId(conversationId, userId)
        );
        if (!isMember) {
            throw new ResourceNotFoundException("Conversation history is not accessible.");
        }

        Pageable pageable = PageRequest.of(0, Math.min(limit, 100));

        if (beforeMessageId == null) {
            // Retrieve latest page
            return messageRepository.findLatestMessages(conversationId, pageable);
        } else {
            // Retrieve older page utilizing the time-sortable UUID as the cursor
            return messageRepository.findMessagesBefore(conversationId, beforeMessageId, pageable);
        }
    }
}