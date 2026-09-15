package com.chat.conversation.repository;

import com.chat.conversation.model.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMember.ConversationMemberId> {
    List<ConversationMember> findByConversationId(UUID conversationId);
    List<ConversationMember> findByUserId(UUID userId);
}