package com.chat.conversation.repository;

import com.chat.conversation.model.ConversationEntity;
import com.chat.conversation.model.ConversationEntity.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    
    @Query("""
        SELECT c FROM ConversationEntity c
        WHERE c.type = :type
          AND EXISTS (
              SELECT 1 FROM ConversationMember m1 
              WHERE m1.conversationId = c.id AND m1.userId = :userId1
          )
          AND EXISTS (
              SELECT 1 FROM ConversationMember m2 
              WHERE m2.conversationId = c.id AND m2.userId = :userId2
          )
        """)
    Optional<ConversationEntity> findDirectConversationBetweenUsers(
            @Param("userId1") UUID userId1, 
            @Param("userId2") UUID userId2,
            @Param("type") ConversationType type
    );
}