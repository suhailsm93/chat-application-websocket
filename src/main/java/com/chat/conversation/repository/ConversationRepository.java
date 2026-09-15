package com.chat.conversation.repository;

import com.chat.conversation.model.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    
    @Query(value = """
        SELECT c.* FROM conversations c
        JOIN conversation_members m1 ON c.id = m1.conversation_id
        JOIN conversation_members m2 ON c.id = m2.conversation_id
        WHERE c.type = 'DIRECT'
          AND m1.user_id = :userId1
          AND m2.user_id = :userId2
        LIMIT 1
        """, nativeQuery = true)
    Optional<ConversationEntity> findDirectConversationBetweenUsers(
            @Param("userId1") UUID userId1, 
            @Param("userId2") UUID userId2
    );
}