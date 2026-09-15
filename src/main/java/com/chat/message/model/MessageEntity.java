package com.chat.message.model;

import com.chat.common.util.UUIDv7Utils;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEntity {

    @Id
    private UUID id; // Generated via UUIDv7

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "client_message_id")
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "is_edited", nullable = false)
    private boolean isEdited;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum MessageType {
        TEXT, IMAGE, VIDEO, FILE, AUDIO
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUIDv7Utils.generateUUIDv7();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (messageType == null) {
            messageType = MessageType.TEXT;
        }
    }
}