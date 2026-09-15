package com.chat.message.repository;

import com.chat.message.model.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    @Query("SELECT m FROM MessageEntity m WHERE m.conversationId = :conversationId ORDER BY m.id DESC")
    List<MessageEntity> findLatestMessages(@Param("conversationId") UUID conversationId, Pageable pageable);

    @Query("SELECT m FROM MessageEntity m WHERE m.conversationId = :conversationId AND m.id < :beforeId ORDER BY m.id DESC")
    List<MessageEntity> findMessagesBefore(@Param("conversationId") UUID conversationId, @Param("beforeId") UUID beforeId, Pageable pageable);

    Optional<MessageEntity> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId, String clientMessageId);
}